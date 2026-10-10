package com.example.planyourtrip.data.trip

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.planyourtrip.data.sync.RoomSyncLocalStore
import com.example.planyourtrip.data.sync.SyncKind
import com.example.planyourtrip.ui.common.split.planTravelerMerge
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

/**
 * Merging a name-only traveler into a member, in Room: one transaction that changes the expenses and settlements as
 * pending edits, deletes a settlement between the two and the traveler as recorded deletes, for this account only.
 */
@RunWith(AndroidJUnit4::class)
class TravelerMergeRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var trips: RoomTripRepository
    private lateinit var expenses: RoomExpenseRepository
    private lateinit var travelers: RoomTravelerRepository
    private lateinit var settlements: RoomSettlementRepository
    private lateinit var sync: RoomSyncLocalStore

    private val clock = Clock.fixed(Instant.parse("2026-10-10T10:00:00Z"), ZoneOffset.UTC)
    private val day = LocalDate.of(2026, 11, 13)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        trips = RoomTripRepository(db.tripDao(), clock)
        expenses = RoomExpenseRepository(db.expenseDao(), db.tripDao(), clock)
        travelers = RoomTravelerRepository(db.travelerDao(), db.tripDao(), clock)
        settlements = RoomSettlementRepository(db.settlementDao(), db.tripDao(), clock)
        sync = RoomSyncLocalStore(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun merge_movesEverythingToTheMember_inOneChangeThatSyncs() = runTest {
        val trip = trips.addTrip("u1", "London", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        // Noa joined with her account (uid "noa") after being added by hand as the traveler "Noa".
        db.openHelper.writableDatabase.execSQL("UPDATE trips SET member_ids = 'u1,noa' WHERE id = $trip")
        val travelerId = checkNotNull(travelers.addTraveler(trip, "u1", "Noa"))
        val key = travelers.observeTravelers(trip, "u1").first().single().uuid
        val hotel = checkNotNull(expenses.addExpense(trip, "u1", input(30_000, paidBy = "u1", split = listOf("u1", key))))
        val shop = checkNotNull(expenses.addExpense(trip, "u1", input(25_000, paidBy = key, split = listOf("u1", key))))
        settlements.addSettlement(trip, "u1", key, "u1", 1_000, day)
        settlements.addSettlement(trip, "u1", "noa", key, 700, day) // to herself once merged
        markAllSynced()

        val result = travelers.mergeIntoMember(trip, "u1", travelerId, "noa", ::planTravelerMerge)

        assertEquals(MergeTravelerResult.MERGED, result)
        assertTrue(travelers.observeTravelers(trip, "u1").first().isEmpty())
        val after = expenses.observeExpenses(trip, "u1").first().associateBy { it.id }
        assertEquals(listOf("u1", "noa"), after.getValue(hotel).splitBetween)
        assertEquals("noa", after.getValue(shop).paidBy)
        val left = settlements.observeSettlements(trip, "u1").first()
        assertEquals(listOf("noa" to "u1"), left.map { it.fromKey to it.toKey })
        // Two expenses and one settlement to upload; the traveler and the settlement to herself as recorded deletes.
        val pending = sync.snapshot("u1", pendingOnly = true).map { it.item.kind to it.item.isDeleted }
        assertEquals(
            listOf(SyncKind.EXPENSE to false, SyncKind.EXPENSE to false, SyncKind.SETTLEMENT to false,
                SyncKind.TRAVELER to true, SyncKind.SETTLEMENT to true).sortedBy { it.toString() },
            pending.sortedBy { it.toString() },
        )
    }

    @Test
    fun aMergeThatWouldChangeTheNumbers_writesNothing() = runTest {
        val trip = trips.addTrip("u1", "London", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17))
        db.openHelper.writableDatabase.execSQL("UPDATE trips SET member_ids = 'u1,noa' WHERE id = $trip")
        val travelerId = checkNotNull(travelers.addTraveler(trip, "u1", "Noa"))
        val key = travelers.observeTravelers(trip, "u1").first().single().uuid
        expenses.addExpense(trip, "u1", input(9_000, paidBy = "u1", split = listOf("u1", "noa", key)))
        markAllSynced()

        assertEquals(MergeTravelerResult.BLOCKED, travelers.mergeIntoMember(trip, "u1", travelerId, "noa", ::planTravelerMerge))
        assertEquals(1, travelers.observeTravelers(trip, "u1").first().size)
        assertTrue(sync.snapshot("u1", pendingOnly = true).isEmpty())
        // Another account can't merge into my trip.
        assertEquals(MergeTravelerResult.NOT_FOUND, travelers.mergeIntoMember(trip, "u2", travelerId, "noa", ::planTravelerMerge))
    }

    private fun input(amount: Long, paidBy: String, split: List<String>) =
        ExpenseInput("Item", amount, ExpenseCategory.OTHER, day, paidBy = paidBy, splitBetween = split)

    private suspend fun markAllSynced() {
        sync.snapshot("u1", pendingOnly = true).forEach { sync.markSynced("u1", it.item) }
    }
}
