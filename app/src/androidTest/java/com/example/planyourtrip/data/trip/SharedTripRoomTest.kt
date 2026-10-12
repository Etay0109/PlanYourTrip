package com.example.planyourtrip.data.trip

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.planyourtrip.data.sync.RoomSyncLocalStore
import com.example.planyourtrip.data.sync.SyncKind
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room side of shared trips (Stage 2): member documents, trip owners, and removing a trip the account lost. */
@RunWith(AndroidJUnit4::class)
class SharedTripRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var trips: RoomTripRepository
    private lateinit var expenses: RoomExpenseRepository
    private lateinit var checklist: RoomChecklistRepository
    private lateinit var members: RoomTripMemberRepository
    private lateinit var sync: RoomSyncLocalStore

    private val clock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        trips = RoomTripRepository(db.tripDao(), clock)
        expenses = RoomExpenseRepository(db.expenseDao(), db.tripDao(), clock)
        checklist = RoomChecklistRepository(db.checklistDao(), db.tripDao(), clock)
        members = RoomTripMemberRepository(db.tripMemberDao())
        sync = RoomSyncLocalStore(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun rome(owner: String = "u1") =
        trips.addTrip(owner, "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))

    private suspend fun uuidOf(id: Long, owner: String = "u1") = trips.observeTrip(id, owner).first()!!.uuid

    @Test
    fun memberDocuments_areReplacedAsAWhole_scopedToTheAccount_andGoWithTheirTrip() = runTest {
        val id = rome()
        val uuid = uuidOf(id)
        sync.replaceMembers(
            "u1",
            mapOf(uuid to listOf(TripMember("u1", "Yael", 1, false), TripMember("noa", "Noa", 2, false))),
        )
        assertEquals(listOf("Yael", "Noa"), members.observeMembers(id, "u1").first().map { it.name })
        assertTrue(members.observeMembers(id, "u2").first().isEmpty())

        sync.replaceMembers("u1", mapOf(uuid to listOf(TripMember("noa", "Noa", 2, true))))
        assertEquals(listOf(TripMember("noa", "Noa", 2, true)), members.observeMembers(id, "u1").first())

        // Another account's copy of a trip with the same uuid is not touched.
        sync.replaceMembers("u2", mapOf(uuid to listOf(TripMember("x", "X", 3, false))))
        assertEquals(1, members.observeMembers(id, "u1").first().size)

        trips.deleteTrip(id, "u1")
        db.query("SELECT COUNT(*) FROM trip_members", null).use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun aNewTripIsMine_andTripOwnersAreReported() = runTest {
        val id = rome()
        val trip = trips.observeTrip(id, "u1").first()!!
        assertEquals("u1", trip.ownerId)
        assertEquals(mapOf(trip.uuid to "u1"), sync.tripOwners("u1"))
        assertTrue(sync.tripOwners("u2").isEmpty())
    }

    @Test
    fun removingALostTrip_dropsItsItems_pendingChanges_andTombstones_onlyForThatTripAndAccount() = runTest {
        val lost = rome()
        val kept = trips.addTrip("u1", "Oslo", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3))
        val otherAccount = rome("u2")
        val lostUuid = uuidOf(lost)
        val dinner = expenses.addExpense(
            lost, "u1", ExpenseInput("Dinner", 12_050, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13), null, "u1", listOf("u1")),
        )!!
        expenses.addExpense(
            lost, "u1", ExpenseInput("Taxi", 3_000, ExpenseCategory.TRANSPORT, LocalDate.of(2026, 11, 13), null, "u1", listOf("u1")),
        )
        expenses.deleteExpense(dinner, "u1") // a tombstone waiting to upload
        checklist.addItem(lost, "u1", ChecklistKind.PACKING, "Charger")
        checklist.addItem(kept, "u1", ChecklistKind.PACKING, "Scarf")
        assertTrue(sync.snapshot("u1", pendingOnly = true).any { it.item.isDeleted })

        sync.removeTrips("u1", setOf(lostUuid))

        assertEquals(listOf("Oslo"), trips.observeTrips("u1").first().map { it.destination })
        val left = sync.snapshot("u1", pendingOnly = false)
        assertTrue(left.none { (it.item.tripUuid ?: it.item.uuid) == lostUuid })
        assertTrue(left.any { it.item.kind == SyncKind.PACKING })
        // The other account's copy of the same trip stays.
        assertEquals(listOf("Rome"), trips.observeTrips("u2").first().map { it.destination })
        assertEquals(otherAccount, trips.observeTrips("u2").first().single().id)
    }
}
