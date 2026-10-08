package com.example.planyourtrip.data.trip

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.planyourtrip.data.currency.ExchangeRateEntity
import com.example.planyourtrip.data.document.DocumentFileStore
import com.example.planyourtrip.data.document.DocumentInput
import com.example.planyourtrip.data.document.DocumentType
import com.example.planyourtrip.data.document.PickedFile
import com.example.planyourtrip.data.document.RoomDocumentRepository
import com.example.planyourtrip.data.sync.ItemData
import com.example.planyourtrip.data.sync.RoomSyncLocalStore
import com.example.planyourtrip.data.sync.SyncItem
import com.example.planyourtrip.data.sync.SyncKind
import com.example.planyourtrip.data.sync.planMerge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class TripDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomTripRepository
    private lateinit var expenses: RoomExpenseRepository
    private lateinit var activities: RoomActivityRepository
    private lateinit var checklist: RoomChecklistRepository
    private lateinit var documents: RoomDocumentRepository
    private lateinit var travelers: RoomTravelerRepository
    private lateinit var settlements: RoomSettlementRepository

    /**
     * The checklist's clock stands still: every item is added in the same millisecond, the case where the order used
     * to depend on chance.
     */
    private val now = Instant.parse("2026-10-07T10:00:00Z")
    private val fixedClock = Clock.fixed(now, ZoneOffset.UTC)

    /** In-memory files: name → size. */
    private val storedFiles = mutableMapOf<String, Long>()
    private val files = object : DocumentFileStore {
        override suspend fun inspect(uri: String) = PickedFile("x.pdf", "application/pdf", 10)
        override suspend fun copyIn(uri: String, fileName: String, maxBytes: Long): Long = 10L.also {
            storedFiles[fileName] = it
        }
        override suspend fun delete(fileName: String) {
            storedFiles.remove(fileName)
        }
        override suspend fun contentUri(fileName: String) = if (fileName in storedFiles) "content://x/$fileName" else null
        override suspend fun sweep(keep: Set<String>) {
            storedFiles.keys.retainAll(keep)
        }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        repository = RoomTripRepository(db.tripDao())
        expenses = RoomExpenseRepository(db.expenseDao(), db.tripDao())
        activities = RoomActivityRepository(db.activityDao(), db.tripDao())
        checklist = RoomChecklistRepository(db.checklistDao(), db.tripDao(), fixedClock)
        documents = RoomDocumentRepository(db.documentDao(), db.tripDao(), files)
        travelers = RoomTravelerRepository(db.travelerDao(), db.tripDao(), fixedClock)
        settlements = RoomSettlementRepository(db.settlementDao(), db.tripDao(), fixedClock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun addTrip_storesFields_assignsColorsRoundRobin_andOrdersByStart() = runTest {
        val lisbonId = repository.addTrip("u1", " Lisbon ", LocalDate.of(2027, 4, 20), LocalDate.of(2027, 4, 26))
        repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))

        val trips = repository.observeTrips("u1").first()
        assertEquals(listOf("Rome", "Lisbon"), trips.map { it.destination })
        assertEquals(TripColor.forIndex(0), trips.first { it.id == lisbonId }.color)
        assertEquals(TripColor.forIndex(1), trips.first { it.destination == "Rome" }.color)
        assertEquals(LocalDate.of(2027, 4, 26), repository.observeTrip(lisbonId, "u1").first()?.endDate)
    }

    @Test
    fun trips_areScopedToTheirOwner() = runTest {
        val id = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        repository.addTrip("u2", "Oslo", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3))

        assertEquals(listOf("Rome"), repository.observeTrips("u1").first().map { it.destination })
        assertEquals(listOf("Oslo"), repository.observeTrips("u2").first().map { it.destination })
        assertNull(repository.observeTrip(id, "u2").first())
        // Each owner's palette starts at the first color.
        assertEquals(TripColor.forIndex(0), repository.observeTrips("u2").first().single().color)
    }

    @Test
    fun updateTrip_andSetBudget_onlyForTheOwner() = runTest {
        val id = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        assertNull(repository.observeTrip(id, "u1").first()?.budgetAgorot) // a new trip has no budget

        repository.updateTrip(id, "u1", " Rome, Italy ", LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 18), null)
        repository.setBudget(id, "u1", 600_000)
        repository.setBudget(id, "u2", 1) // another account changes nothing
        repository.updateTrip(id, "u2", "Oslo", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), "USD")

        val trip = repository.observeTrip(id, "u1").first()!!
        assertEquals("Rome, Italy", trip.destination)
        assertEquals(LocalDate.of(2026, 11, 13), trip.startDate)
        assertEquals(600_000L, trip.budgetAgorot)
    }

    @Test
    fun currency_isSetOnlyForTheOwner_andChangedByEditingTheTrip() = runTest {
        val id = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        assertNull(repository.observeTrip(id, "u1").first()?.currencyCode) // a new trip has no currency

        repository.setCurrency(id, "u1", "EUR")
        repository.setCurrency(id, "u2", "USD") // another account changes nothing
        assertEquals("EUR", repository.observeTrip(id, "u1").first()?.currencyCode)

        repository.updateTrip(id, "u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), "CHF")
        assertEquals("CHF", repository.observeTrip(id, "u1").first()?.currencyCode)
        repository.updateTrip(id, "u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), null)
        assertNull(repository.observeTrip(id, "u1").first()?.currencyCode)

        val withCurrency = repository.addTrip("u1", "Oslo", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), "NOK")
        assertEquals("NOK", repository.observeTrip(withCurrency, "u1").first()?.currencyCode)
    }

    @Test
    fun exchangeRates_areCachedExactly_andReplacedPerCurrency() = runTest {
        val dao = db.exchangeRateDao()
        val fetched = Instant.parse("2026-10-05T08:00:00Z")
        dao.upsert(ExchangeRateEntity("EUR", BigDecimal("3.4408"), LocalDate.of(2026, 10, 1), fetched))
        dao.upsert(ExchangeRateEntity("EUR", BigDecimal("3.44100"), LocalDate.of(2026, 10, 2), fetched))

        val rate = dao.observe("EUR").first()!!
        assertEquals(BigDecimal("3.44100"), rate.rate)
        assertEquals(LocalDate.of(2026, 10, 2), rate.rateDate)
        assertNull(dao.observe("USD").first())
    }

    @Test
    fun expenses_newestFirst_scopedToOwner_andEditable() = runTest {
        val trip = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val older = expenses.addExpense(trip, "u1", " Flights ", 180_000, ExpenseCategory.FLIGHTS, LocalDate.of(2026, 9, 1))!!
        val newer = expenses.addExpense(trip, "u1", "Dinner", 12_050, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13))!!
        assertNull(expenses.addExpense(trip, "u2", "Sneaky", 1, ExpenseCategory.OTHER, LocalDate.of(2026, 11, 13)))

        assertEquals(listOf(newer, older), expenses.observeExpenses(trip, "u1").first().map { it.id })
        assertEquals("Flights", expenses.observeExpense(older, "u1").first()?.name)
        assertEquals(emptyList<Expense>(), expenses.observeExpenses(trip, "u2").first())
        assertNull(expenses.observeExpense(older, "u2").first())

        expenses.updateExpense(newer, "u1", "Dinner", 13_000, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13))
        expenses.deleteExpense(older, "u2") // another account deletes nothing
        assertEquals(listOf(13_000L, 180_000L), expenses.observeExpenses(trip, "u1").first().map { it.amountAgorot })

        expenses.deleteExpense(older, "u1")
        assertEquals(listOf(newer), expenses.observeExpenses(trip, "u1").first().map { it.id })
    }

    @Test
    fun deletingATrip_deletesItsExpenses() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val oslo = repository.addTrip("u1", "Oslo", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3))
        expenses.addExpense(rome, "u1", "Dinner", 12_050, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13))
        expenses.addExpense(oslo, "u1", "Taxi", 9_000, ExpenseCategory.TRANSPORT, LocalDate.of(2026, 12, 1))

        // No delete-trip API exists yet; delete the row directly to check the foreign key cascade.
        db.openHelper.writableDatabase.execSQL("DELETE FROM trips WHERE id = $rome")

        val remaining = db.openHelper.readableDatabase.query("SELECT trip_id FROM expenses").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        assertEquals(listOf(oslo), remaining)
    }

    private fun input(title: String, date: LocalDate, time: LocalTime? = null) = ActivityInput(
        date = date,
        startTime = time,
        title = title,
        type = ActivityType.DINING,
        note = " Pinsa ",
        location = "  ",
        estimatedCostAgorot = 2_500,
        booked = true,
    )

    @Test
    fun activities_storeAllFields_scopedToOwner_andEditable() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val id = activities.addActivity(rome, "u1", input(" Lunch ", LocalDate.of(2026, 11, 12), LocalTime.of(13, 30)))!!
        assertNull(activities.addActivity(rome, "u2", input("Sneaky", LocalDate.of(2026, 11, 12))))

        val stored = activities.observeActivity(id, "u1").first()!!
        assertEquals("Lunch", stored.title)
        assertEquals(LocalTime.of(13, 30), stored.startTime)
        assertEquals("Pinsa", stored.note)
        assertNull(stored.location) // blank → null
        assertEquals(2_500L, stored.estimatedCostAgorot)
        assertTrue(stored.booked)
        assertEquals(ActivityType.DINING, stored.type)
        assertNull(activities.observeActivity(id, "u2").first())
        assertTrue(activities.observeActivities(rome, "u2").first().isEmpty())

        activities.updateActivity(id, "u2", input("Changed", LocalDate.of(2026, 11, 13))) // ignored
        activities.updateActivity(id, "u1", input("Dinner", LocalDate.of(2026, 11, 14)).copy(startTime = null))
        val updated = activities.observeActivities(rome, "u1").first().single()
        assertEquals("Dinner", updated.title)
        assertNull(updated.startTime)
        assertEquals(LocalDate.of(2026, 11, 14), updated.date)

        activities.deleteActivity(id, "u2") // another account deletes nothing
        assertEquals(1, activities.observeActivities(rome, "u1").first().size)
        activities.deleteActivity(id, "u1")
        assertTrue(activities.observeActivities(rome, "u1").first().isEmpty())
    }

    @Test
    fun editingTripDates_keepsActivities_andDeletingATripDeletesThem() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val oslo = repository.addTrip("u1", "Oslo", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3))
        activities.addActivity(rome, "u1", input("Colosseum", LocalDate.of(2026, 11, 16)))
        activities.addActivity(oslo, "u1", input("Fjord", LocalDate.of(2026, 12, 2)))

        repository.updateTrip(rome, "u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14), null)
        assertEquals(1, activities.observeActivities(rome, "u1").first().size)

        db.openHelper.writableDatabase.execSQL("DELETE FROM trips WHERE id = $rome")
        val remaining = db.openHelper.readableDatabase.query("SELECT trip_id FROM activities").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        assertEquals(listOf(oslo), remaining)
    }

    @Test
    fun foreignExpenses_storeTheOriginalExactly_andUpdatesCanClearIt() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val original = ForeignAmount(BigDecimal("1500"), "JPY", BigDecimal("0.02512"), LocalDate.of(2026, 10, 2))
        val id = expenses.addExpense(
            rome, "u1", "Souvenirs", 3_768, ExpenseCategory.SHOPPING, LocalDate.of(2026, 11, 13), original,
        )!!
        val stored = expenses.observeExpense(id, "u1").first()!!
        assertEquals(3_768L, stored.amountAgorot)
        assertEquals(original, stored.original) // BigDecimal text round-trips exactly

        // Switched to shekels in the form: the original is cleared.
        expenses.updateExpense(id, "u1", "Souvenirs", 4_000, ExpenseCategory.SHOPPING, LocalDate.of(2026, 11, 13), null)
        assertNull(expenses.observeExpense(id, "u1").first()!!.original)

        // A shekel expense has no original.
        val ils = expenses.addExpense(rome, "u1", "Taxi", 9_000, ExpenseCategory.TRANSPORT, LocalDate.of(2026, 11, 12))!!
        assertNull(expenses.observeExpense(ils, "u1").first()!!.original)
    }

    @Test
    fun changes_arePendingUntilSynced_andDeletesAreRecordedForTheCloud() = runTest {
        val sync = RoomSyncLocalStore(db)
        val trip = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val expense = expenses.addExpense(trip, "u1", "Dinner", 12_050, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13))!!
        assertEquals(2, sync.pendingCount("u1").first())
        assertEquals(0, sync.pendingCount("u2").first())

        val items = sync.snapshot("u1", pendingOnly = true)
        items.forEach { sync.markSynced("u1", it.item) }
        assertEquals(0, sync.pendingCount("u1").first())
        assertTrue(items.map { it.item.uuid }.none { it.isBlank() })

        expenses.deleteExpense(expense, "u1")
        val tombstone = sync.snapshot("u1", pendingOnly = true).single().item
        assertTrue(tombstone.isDeleted)
        assertEquals(items.first { it.item.kind == SyncKind.TRIP }.item.uuid, tombstone.tripUuid)

        sync.deleteAllFor("u1")
        assertTrue(repository.observeTrips("u1").first().isEmpty())
        assertEquals(0, sync.pendingCount("u1").first())
    }

    @Test
    fun restoringADeletedActivity_bringsItBack_asANewPendingItem() = runTest {
        val sync = RoomSyncLocalStore(db)
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val visit = activities.addActivity(rome, "u1", input("Coins", LocalDate.of(2026, 11, 13)).copy(location = "Trevi"))!!
        val before = activities.observeActivity(visit, "u1").first()!!

        val deleted = activities.deleteActivity(visit, "u1")!!
        assertEquals(DeletedActivity(before), deleted)
        assertNull(activities.observeActivity(visit, "u1").first())
        assertNull(activities.restoreActivity(deleted, "other")) // never into another account's trip

        val restoredId = activities.restoreActivity(deleted, "u1")!!
        assertEquals(before.copy(id = restoredId), activities.observeActivity(restoredId, "u1").first())
        // The old item's delete and the new item both wait to upload; no uuid is reused.
        val pending = sync.snapshot("u1", pendingOnly = true).map { it.item }
        assertEquals(1, pending.count { it.isDeleted })
        assertEquals(1, pending.count { !it.isDeleted && it.kind == SyncKind.ACTIVITY })
        assertEquals(pending.size, pending.map { it.uuid }.toSet().size)
    }

    // ---------- Prep tab: checklist items and documents ----------

    @Test
    fun checklist_scopedToOwner_inTheOrderAdded_andEditable() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val passport = checklist.addItem(rome, "u1", ChecklistKind.BEFORE_TRIP, "  Passport ")!!
        checklist.addItems(rome, "u1", ChecklistKind.PACKING, listOf("Charger", " ", "Adapter"))
        val insurance = checklist.addItem(rome, "u1", ChecklistKind.BEFORE_TRIP, "Insurance")!!
        assertNull(checklist.addItem(rome, "u2", ChecklistKind.PACKING, "Not mine")) // another account's trip
        assertNull(checklist.addItem(rome, "u1", ChecklistKind.PACKING, "   "))

        val items = checklist.observeItems(rome, "u1").first()
        assertEquals(listOf("Passport", "Charger", "Adapter", "Insurance"), items.map { it.text })
        assertTrue(checklist.observeItems(rome, "u2").first().isEmpty())

        checklist.setChecked(passport, "u1", true)
        checklist.updateText(insurance, "u1", " Travel insurance ")
        checklist.setDueDate(insurance, "u1", LocalDate.of(2026, 11, 1))
        val charger = items.first { it.text == "Charger" }.id
        checklist.setDueDate(charger, "u1", LocalDate.of(2026, 11, 1)) // packing: ignored
        checklist.setChecked(insurance, "u2", true) // another account: ignored

        val after = checklist.observeItems(rome, "u1").first().associateBy { it.id }
        assertTrue(after.getValue(passport).checked)
        assertEquals("Travel insurance", after.getValue(insurance).text)
        assertEquals(LocalDate.of(2026, 11, 1), after.getValue(insurance).dueDate)
        assertEquals(false, after.getValue(insurance).checked)
        assertNull(after.getValue(charger).dueDate)

        checklist.setDueDate(insurance, "u1", null)
        assertNull(checklist.observeItems(rome, "u1").first().first { it.id == insurance }.dueDate)
    }

    @Test
    fun checklist_deleteIsRecorded_restoreComesBackAtItsPlace_asANewPendingItem() = runTest {
        val sync = RoomSyncLocalStore(db)
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val first = checklist.addItem(rome, "u1", ChecklistKind.BEFORE_TRIP, "Passport")!!
        checklist.addItem(rome, "u1", ChecklistKind.BEFORE_TRIP, "Insurance")
        sync.snapshot("u1", pendingOnly = true).forEach { sync.markSynced("u1", it.item) }

        val deleted = checklist.deleteItem(first, "u1")!!
        assertNull(checklist.deleteItem(first, "u1"))
        val tombstone = sync.snapshot("u1", pendingOnly = true).single().item
        assertTrue(tombstone.isDeleted)
        assertEquals(SyncKind.CHECKLIST, tombstone.kind)

        assertNull(checklist.restoreItem(deleted, "u2"))
        val restored = checklist.restoreItem(deleted, "u1")!!
        assertEquals(listOf("Passport", "Insurance"), checklist.observeItems(rome, "u1").first().map { it.text })
        val pending = sync.snapshot("u1", pendingOnly = true).map { it.item }
        assertEquals(1, pending.count { it.isDeleted })
        assertEquals(1, pending.count { !it.isDeleted && it.kind == SyncKind.CHECKLIST })
        assertEquals(pending.size, pending.map { it.uuid }.toSet().size)
        assertTrue(restored != first)
    }

    @Test
    fun checklist_isPendingUntilSynced_andGoesWithItsTrip() = runTest {
        val sync = RoomSyncLocalStore(db)
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        checklist.addItem(rome, "u1", ChecklistKind.PACKING, "Charger")
        assertEquals(2, sync.pendingCount("u1").first())
        sync.snapshot("u1", pendingOnly = true).forEach { sync.markSynced("u1", it.item) }
        assertEquals(0, sync.pendingCount("u1").first())

        sync.deleteAllFor("u1")
        assertEquals(0, db.query("SELECT * FROM checklist_items", null).use { it.count })
    }

    @Test
    fun suggestedItems_alwaysKeepTheirOrder_evenWhenEverythingIsAddedInTheSameMillisecond() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val suggested = listOf("Passport", "Charger", "Adapter", "Sunscreen", "Toothbrush", "Medicines")
        checklist.addItem(rome, "u1", ChecklistKind.PACKING, "First")
        checklist.addItems(rome, "u1", ChecklistKind.PACKING, suggested)
        checklist.addItem(rome, "u1", ChecklistKind.PACKING, "Last")

        fun texts(items: List<ChecklistItem>) = items.sortedWith(compareBy({ it.createdAt }, { it.id })).map { it.text }
        val expected = listOf("First") + suggested + "Last"
        val items = checklist.observeItems(rome, "u1").first()
        assertEquals(expected, items.map { it.text }) // the query's order
        assertEquals(expected, texts(items))
        // Increasing creation times, 1 ms apart, starting at the clock's time.
        assertEquals((0L until expected.size.toLong()).map { now.toEpochMilli() + it }, items.map { it.createdAt })

        // Deleting and restoring an item in the middle brings it back to its place.
        val adapter = items.first { it.text == "Adapter" }
        checklist.restoreItem(checklist.deleteItem(adapter.id, "u1")!!, "u1")
        assertEquals(expected, checklist.observeItems(rome, "u1").first().map { it.text })

        // A clock set back still adds at the end.
        val earlier = RoomChecklistRepository(
            db.checklistDao(), db.tripDao(), Clock.fixed(now.minusSeconds(3_600), ZoneOffset.UTC),
        )
        earlier.addItems(rome, "u1", ChecklistKind.PACKING, listOf("Hat", "Book"))
        assertEquals(expected + listOf("Hat", "Book"), checklist.observeItems(rome, "u1").first().map { it.text })
    }

    @Test
    fun checklistItemsFromTheCloud_areStoredInCreationOrder_whateverOrderTheyArriveIn() = runTest {
        val sync = RoomSyncLocalStore(db)
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val tripUuid = db.tripDao().uuidOf(rome, "u1")!!
        fun item(uuid: String, text: String, createdAt: Long) = SyncItem(
            kind = SyncKind.CHECKLIST,
            uuid = uuid,
            tripUuid = tripUuid,
            updatedAt = 100,
            data = ItemData.ChecklistData(ChecklistKind.PACKING, text, false, null, createdAt),
        )
        // Another device's suggested items, plus two items added there in the same millisecond (ties go by uuid).
        val cloud = listOf(
            item("u-5", "Tie b", 50), item("u-3", "Adapter", 12), item("u-1", "Passport", 10),
            item("u-4", "Tie a", 50), item("u-2", "Charger", 11),
        )

        sync.apply("u1", planMerge(local = emptyList(), cloud = cloud))

        val stored = checklist.observeItems(rome, "u1").first()
        assertEquals(listOf("Passport", "Charger", "Adapter", "Tie a", "Tie b"), stored.map { it.text })
        // Rows were inserted in that order, so the ids (the tie-break of the query) follow it too.
        assertEquals(stored.map { it.id }.sorted(), stored.map { it.id })
    }

    @Test
    fun documents_scopedToOwner_surviveSignOut_andComeBackWithTheTrip() = runTest {
        val sync = RoomSyncLocalStore(db)
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val tripUuid = db.tripDao().uuidOf(rome, "u1")!!
        val id = documents.addDocument(
            rome, "u1", DocumentInput(" Tickets ", DocumentType.FLIGHT, " "), "content://picked", "application/pdf",
        )!!
        assertNull(documents.addDocument(rome, "u2", DocumentInput("x", DocumentType.OTHER, null), "u", "image/png"))

        val stored = documents.observeDocuments(rome, "u1").first().single()
        assertEquals("Tickets", stored.name)
        assertNull(stored.detail)
        assertTrue(stored.fileName.endsWith(".pdf"))
        assertEquals(setOf(stored.fileName), storedFiles.keys)
        assertTrue(documents.observeDocuments(rome, "u2").first().isEmpty())
        assertNull(documents.fileToView(id, "u2"))
        assertEquals("application/pdf", documents.fileToView(id, "u1")!!.mimeType)

        // Sign out: the trips go, the document and its file stay.
        sync.deleteAllFor("u1")
        assertEquals(1, db.query("SELECT * FROM documents", null).use { it.count })
        assertEquals(1, storedFiles.size)

        // Signed in again: the trip comes back from the cloud with the same uuid → the document is there again.
        db.tripDao().insert(
            TripEntity(
                ownerId = "u1", destination = "Rome", startDate = LocalDate.of(2026, 11, 12),
                endDate = LocalDate.of(2026, 11, 17), color = TripColor.SKY, uuid = tripUuid, pendingSync = false,
            )
        )
        val back = repository.observeTrips("u1").first().single().id
        assertEquals("Tickets", documents.observeDocuments(back, "u1").first().single().name)
    }

    @Test
    fun documents_ofTripsThatAreGone_areRemovedWithTheirFiles() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val oslo = repository.addTrip("u1", "Oslo", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3))
        val bobs = repository.addTrip("u2", "Paris", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3))
        documents.addDocument(rome, "u1", DocumentInput("Hotel", DocumentType.LODGING, null), "c1", "image/jpeg")
        documents.addDocument(oslo, "u1", DocumentInput("Flight", DocumentType.FLIGHT, null), "c2", "application/pdf")
        documents.addDocument(bobs, "u2", DocumentInput("Ticket", DocumentType.TICKET, null), "c3", "application/pdf")

        // Oslo was deleted on another device; the sync removed it here.
        db.openHelper.writableDatabase.execSQL("DELETE FROM trips WHERE id = $oslo")
        documents.removeOrphans("u1")

        assertEquals(listOf("Hotel"), documents.observeDocuments(rome, "u1").first().map { it.name })
        assertEquals(listOf("Ticket"), documents.observeDocuments(bobs, "u2").first().map { it.name })
        assertEquals(2, storedFiles.size)

        // A stray file (interrupted copy) is swept; the documents' files stay.
        storedFiles["stray.pdf"] = 1
        documents.sweepFiles()
        assertEquals(2, storedFiles.size)

        val hotel = documents.observeDocuments(rome, "u1").first().single()
        documents.updateDocument(hotel.id, "u1", DocumentInput("Hotel Artemide", DocumentType.LODGING, "5 nights"))
        documents.updateDocument(hotel.id, "u2", DocumentInput("Hacked", DocumentType.OTHER, null))
        assertEquals("5 nights", documents.observeDocuments(rome, "u1").first().single().detail)
        assertEquals("Hotel Artemide", documents.observeDocuments(rome, "u1").first().single().name)
        documents.deleteDocument(hotel.id, "u1")
        assertTrue(documents.observeDocuments(rome, "u1").first().isEmpty())
        assertEquals(1, storedFiles.size)
    }

    // ---------- Deleting a trip ----------

    private fun count(sql: String, vararg args: Any): Int =
        db.query(sql, args).use { c -> c.moveToFirst(); c.getInt(0) }

    @Test
    fun deletingATrip_leavesNoExpensesActivitiesChecklistItemsOrDocumentsBehind() = runTest {
        val sync = RoomSyncLocalStore(db)
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val oslo = repository.addTrip("u1", "Oslo", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3))
        for (trip in listOf(rome, oslo)) {
            val noa = travelers.addTraveler(trip, "u1", "Noa")!!
            val noaKey = travelers.observeTravelers(trip, "u1").first().single { it.id == noa }.uuid
            expenses.addExpense(
                trip, "u1", "Dinner", 12_050, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13),
                paidBy = noaKey, splitBetween = listOf(ME, noaKey),
            )
            settlements.addSettlement(trip, "u1", ME, noaKey, 6_025, LocalDate.of(2026, 11, 14))
            activities.addActivity(trip, "u1", input("Museum", LocalDate.of(2026, 11, 13)))
            checklist.addItem(trip, "u1", ChecklistKind.PACKING, "Charger")
            documents.addDocument(trip, "u1", DocumentInput("Ticket", DocumentType.TICKET, null), "c$trip", "application/pdf")
        }
        val osloFile = documents.observeDocuments(oslo, "u1").first().single().fileName
        val romeUuid = db.tripDao().uuidOf(rome, "u1")!!
        sync.snapshot("u1", pendingOnly = true).forEach { sync.markSynced("u1", it.item) }

        // Another account can't delete it.
        assertNull(repository.deleteTrip(rome, "u2"))
        assertEquals(2, repository.observeTrips("u1").first().size)

        val deletedUuid = repository.deleteTrip(rome, "u1")
        assertEquals(romeUuid, deletedUuid)
        documents.deleteForTrip(deletedUuid!!, "u1")

        assertEquals(listOf(oslo), repository.observeTrips("u1").first().map { it.id })
        assertEquals(0, count("SELECT COUNT(*) FROM expenses WHERE trip_id = ?", rome))
        assertEquals(0, count("SELECT COUNT(*) FROM activities WHERE trip_id = ?", rome))
        assertEquals(0, count("SELECT COUNT(*) FROM checklist_items WHERE trip_id = ?", rome))
        assertEquals(0, count("SELECT COUNT(*) FROM travelers WHERE trip_id = ?", rome))
        assertEquals(0, count("SELECT COUNT(*) FROM settlements WHERE trip_id = ?", rome))
        assertEquals(0, count("SELECT COUNT(*) FROM documents WHERE trip_uuid = ?", romeUuid))
        assertEquals(setOf(osloFile), storedFiles.keys) // Rome's file is gone, Oslo's stays

        // Oslo keeps everything.
        assertEquals(1, count("SELECT COUNT(*) FROM expenses WHERE trip_id = ?", oslo))
        assertEquals(1, count("SELECT COUNT(*) FROM activities WHERE trip_id = ?", oslo))
        assertEquals(1, count("SELECT COUNT(*) FROM checklist_items WHERE trip_id = ?", oslo))
        assertEquals(1, count("SELECT COUNT(*) FROM travelers WHERE trip_id = ?", oslo))
        assertEquals(1, count("SELECT COUNT(*) FROM settlements WHERE trip_id = ?", oslo))
        assertEquals(1, documents.observeDocuments(oslo, "u1").first().size)

        // Only the trip's delete waits to upload; the cloud tombstones its children.
        val pending = sync.snapshot("u1", pendingOnly = true).map { it.item }
        assertEquals(1, pending.size)
        assertEquals(SyncKind.TRIP, pending.single().kind)
        assertEquals(romeUuid, pending.single().uuid)
        assertTrue(pending.single().isDeleted)
    }

    @Test
    fun expenses_storePayerAndSplit_andOldDefaultsArePaidByMeNotShared() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val plain = expenses.addExpense(rome, "u1", "SIM", 5_000, ExpenseCategory.OTHER, LocalDate.of(2026, 11, 12))!!
        val shared = expenses.addExpense(
            rome, "u1", "Dinner", 18_000, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13),
            paidBy = "r-noa", splitBetween = listOf(ME, "r-noa", "r-amit", ME),
        )!!
        val list = expenses.observeExpenses(rome, "u1").first()
        assertEquals(ME, list.single { it.id == plain }.paidBy)
        assertEquals(listOf(ME), list.single { it.id == plain }.splitBetween)
        assertEquals("r-noa", list.single { it.id == shared }.paidBy)
        assertEquals(listOf(ME, "r-noa", "r-amit"), list.single { it.id == shared }.splitBetween) // no duplicates

        // "Not shared" after an edit; an empty split means the payer only.
        expenses.updateExpense(
            shared, "u1", "Dinner", 18_000, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13), null,
            paidBy = "r-amit", splitBetween = emptyList(),
        )
        val edited = expenses.observeExpense(shared, "u1").first()!!
        assertEquals("r-amit", edited.paidBy)
        assertEquals(listOf("r-amit"), edited.splitBetween)
    }

    @Test
    fun travelers_scopedToOwner_inTheOrderAdded_withRoundRobinColors_andRenamable() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        // Same millisecond (fixed clock): the order still follows the adds.
        val noa = travelers.addTraveler(rome, "u1", " Noa ")!!
        travelers.addTraveler(rome, "u1", "Amit")
        assertNull(travelers.addTraveler(rome, "u2", "Mallory"))
        assertNull(travelers.addTraveler(rome, "u1", "  "))

        val list = travelers.observeTravelers(rome, "u1").first()
        assertEquals(listOf("Noa", "Amit"), list.map { it.name })
        assertEquals(listOf(TravelerColor.forIndex(0), TravelerColor.forIndex(1)), list.map { it.color })
        assertTrue(travelers.observeTravelers(rome, "u2").first().isEmpty())

        travelers.renameTraveler(noa, "u2", "Hacked")
        travelers.renameTraveler(noa, "u1", " Noa B ")
        assertEquals("Noa B", travelers.observeTravelers(rome, "u1").first().first().name)
    }

    @Test
    fun removingATraveler_isBlockedWhilePayerSplitOrSettlementUseThem_andRecordedOtherwise() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        val ids = listOf("Noa", "Amit", "Dana", "Yoni").map { travelers.addTraveler(rome, "u1", it)!! }
        val keys = travelers.observeTravelers(rome, "u1").first().map { it.uuid }
        val (noa, amit, dana, yoni) = keys
        expenses.addExpense(
            rome, "u1", "Dinner", 9_000, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13),
            paidBy = noa, splitBetween = listOf(ME, amit),
        )
        settlements.addSettlement(rome, "u1", dana, ME, 1_000, LocalDate.of(2026, 11, 14))

        assertEquals(RemoveTravelerResult.IN_USE, travelers.removeTraveler(ids[0], "u1")) // paid
        assertEquals(RemoveTravelerResult.IN_USE, travelers.removeTraveler(ids[1], "u1")) // in a split
        assertEquals(RemoveTravelerResult.IN_USE, travelers.removeTraveler(ids[2], "u1")) // in a settlement
        assertEquals(RemoveTravelerResult.NOT_FOUND, travelers.removeTraveler(ids[3], "u2"))
        assertEquals(RemoveTravelerResult.REMOVED, travelers.removeTraveler(ids[3], "u1"))

        assertEquals(listOf(noa, amit, dana), travelers.observeTravelers(rome, "u1").first().map { it.uuid })
        val tombstone = RoomSyncLocalStore(db).snapshot("u1", pendingOnly = true).map { it.item }
            .single { it.isDeleted }
        assertEquals(SyncKind.TRAVELER, tombstone.kind)
        assertEquals(yoni, tombstone.uuid)

        // Once the settlement is deleted, Dana can go.
        val paid = settlements.observeSettlements(rome, "u1").first().single()
        settlements.deleteSettlement(paid.id, "u1")
        assertEquals(RemoveTravelerResult.REMOVED, travelers.removeTraveler(ids[2], "u1"))
    }

    @Test
    fun settlements_newestFirst_scopedToOwner_validated_andDeletesAreRecorded() = runTest {
        val rome = repository.addTrip("u1", "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        settlements.addSettlement(rome, "u1", ME, "r-noa", 3_000, LocalDate.of(2026, 11, 13))
        settlements.addSettlement(rome, "u1", "r-amit", ME, 1_500, LocalDate.of(2026, 11, 15))
        assertNull(settlements.addSettlement(rome, "u2", ME, "r-noa", 100, LocalDate.of(2026, 11, 15)))
        assertNull(settlements.addSettlement(rome, "u1", ME, "r-noa", 0, LocalDate.of(2026, 11, 15)))
        assertNull(settlements.addSettlement(rome, "u1", ME, ME, 100, LocalDate.of(2026, 11, 15)))

        val list = settlements.observeSettlements(rome, "u1").first()
        assertEquals(listOf(1_500L, 3_000L), list.map { it.amountAgorot })
        assertTrue(settlements.observeSettlements(rome, "u2").first().isEmpty())

        val sync = RoomSyncLocalStore(db)
        assertEquals(2, sync.snapshot("u1", pendingOnly = true).count { it.item.kind == SyncKind.SETTLEMENT })
        settlements.deleteSettlement(list.first().id, "u2") // not theirs: nothing happens
        settlements.deleteSettlement(list.first().id, "u1")
        assertEquals(listOf(3_000L), settlements.observeSettlements(rome, "u1").first().map { it.amountAgorot })
        assertTrue(
            sync.snapshot("u1", pendingOnly = true).any { it.item.isDeleted && it.item.kind == SyncKind.SETTLEMENT }
        )
    }

    @Test
    fun travelersSettlementsAndSplitsFromTheCloud_areStored_andUploadedBackUnchanged() = runTest {
        val sync = RoomSyncLocalStore(db)
        val trip = SyncItem(
            SyncKind.TRIP, "t-1", null, 10,
            ItemData.TripData("Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY, null, null),
        )
        val noa = SyncItem(SyncKind.TRAVELER, "r-1", "t-1", 10, ItemData.TravelerData("Noa", TravelerColor.SKY, 3))
        val dinner = SyncItem(
            SyncKind.EXPENSE, "e-1", "t-1", 10,
            ItemData.ExpenseData(
                "Dinner", 18_000, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13), null,
                paidBy = "r-1", splitBetween = listOf(ME, "r-1"),
            ),
        )
        val paid = SyncItem(
            SyncKind.SETTLEMENT, "s-1", "t-1", 10,
            ItemData.SettlementData(ME, "r-1", 9_000, LocalDate.of(2026, 11, 14), 4),
        )
        sync.apply("u1", planMerge(emptyList(), listOf(paid, dinner, noa, trip)))

        val local = sync.snapshot("u1", pendingOnly = false)
        assertTrue(local.none { it.pending })
        assertEquals(setOf(trip, noa, dinner, paid), local.map { it.item }.toSet())
    }
}
