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
import com.example.planyourtrip.data.sync.RoomSyncLocalStore
import com.example.planyourtrip.data.sync.SyncKind
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class TripDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomTripRepository
    private lateinit var expenses: RoomExpenseRepository
    private lateinit var activities: RoomActivityRepository
    private lateinit var checklist: RoomChecklistRepository
    private lateinit var documents: RoomDocumentRepository

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
        checklist = RoomChecklistRepository(db.checklistDao(), db.tripDao())
        documents = RoomDocumentRepository(db.documentDao(), db.tripDao(), files)
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
}
