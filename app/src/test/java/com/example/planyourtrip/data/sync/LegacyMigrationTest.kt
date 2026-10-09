package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.sync.SyncTestData.activity
import com.example.planyourtrip.data.sync.SyncTestData.budget
import com.example.planyourtrip.data.sync.SyncTestData.checklistItem
import com.example.planyourtrip.data.sync.SyncTestData.deletedAt
import com.example.planyourtrip.data.sync.SyncTestData.expense
import com.example.planyourtrip.data.sync.SyncTestData.renamed
import com.example.planyourtrip.data.sync.SyncTestData.settlement
import com.example.planyourtrip.data.sync.SyncTestData.trip
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.LEGACY_ME_KEY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMigrationTest {

    /** A legacy trip document: no owner or members yet. */
    private fun legacyTrip(uuid: String, updatedAt: Long) =
        trip(uuid, updatedAt).let { it.copy(data = (it.data as ItemData.TripData).copy(ownerId = "", memberIds = emptyList())) }

    private fun packing(uuid: String, updatedAt: Long) = checklistItem(uuid, "t1", updatedAt, text = "Charger").let {
        it.copy(data = (it.data as ItemData.ChecklistData).copy(list = ChecklistKind.PACKING, dueDate = null))
    }

    // ---------- convertLegacy ----------

    @Test
    fun theKeyMe_becomesTheAccountsUid_inPayerSplitAndSettlements() {
        val shared = expense("e1", "t1", 10, paidBy = LEGACY_ME_KEY).let {
            it.copy(data = (it.data as ItemData.ExpenseData).copy(splitBetween = listOf("r1", LEGACY_ME_KEY)))
        }
        val paidByNoa = expense("e2", "t1", 10, paidBy = "r1").let {
            it.copy(data = (it.data as ItemData.ExpenseData).copy(splitBetween = listOf(LEGACY_ME_KEY)))
        }
        val paid = settlement("s1", "t1", 10, from = "r1", to = LEGACY_ME_KEY)

        val converted = convertLegacy(listOf(shared, paidByNoa, paid), uid = "alice").associateBy { it.uuid }

        val e1 = converted.getValue("e1").data as ItemData.ExpenseData
        assertEquals("alice", e1.paidBy)
        assertEquals(listOf("r1", "alice"), e1.splitBetween)
        val e2 = converted.getValue("e2").data as ItemData.ExpenseData
        assertEquals("r1", e2.paidBy)
        assertEquals(listOf("alice"), e2.splitBetween)
        val s1 = converted.getValue("s1").data as ItemData.SettlementData
        assertEquals("r1", s1.fromKey)
        assertEquals("alice", s1.toKey)
        // Nothing else changes, the time stays (the newer change keeps winning).
        assertEquals(listOf(10L, 10L, 10L), converted.values.map { it.updatedAt })
    }

    @Test
    fun aTrip_getsTheAccountAsOwnerAndOnlyMember() {
        val converted = convertLegacy(listOf(legacyTrip("t1", 10)), uid = "alice").single()

        assertEquals(trip("t1", 10, owner = "alice"), converted)
    }

    @Test
    fun packingItems_becomePersonal_beforeTheTripItemsStayShared_andTheBudgetIsKept() {
        val converted = convertLegacy(
            listOf(checklistItem("c1", "t1", 10), packing("c2", 10), budget("t1", 10)), uid = "alice",
        ).associateBy { it.kind to it.uuid }

        assertTrue((SyncKind.CHECKLIST to "c1") in converted)
        assertEquals(SyncKind.PACKING, converted.getValue(SyncKind.PACKING to "c2").kind)
        assertEquals(budget("t1", 10), converted.getValue(SyncKind.BUDGET to "t1"))
    }

    @Test
    fun aChecklistTombstone_becomesATombstoneOfBothLists() {
        // A legacy tombstone doesn't say which list the item was in.
        val converted = convertLegacy(listOf(checklistItem("c1", "t1", 10).deletedAt(20)), uid = "alice")

        assertEquals(setOf(SyncKind.CHECKLIST, SyncKind.PACKING), converted.map { it.kind }.toSet())
        assertTrue(converted.all { it.isDeleted && it.updatedAt == 20L })
    }

    // ---------- planLegacyCopy / isLegacyCopyComplete ----------

    private val legacy = convertLegacy(
        listOf(legacyTrip("t1", 10), budget("t1", 10), expense("e1", "t1", 10), activity("a1", "t1", 10)),
        uid = "alice",
    )

    @Test
    fun everything_isCopied_toAnEmptyNewLayout_tripsFirst() {
        val copy = planLegacyCopy(legacy, current = emptyList())

        assertEquals(legacy.toSet(), copy.toSet())
        assertEquals(SyncKind.TRIP, copy.first().kind)
        assertFalse(isLegacyCopyComplete(legacy, current = emptyList()))
    }

    @Test
    fun afterTheCopy_nothingIsLeft_soARepeatedRunWritesNothing() {
        assertTrue(planLegacyCopy(legacy, current = legacy).isEmpty())
        assertTrue(isLegacyCopyComplete(legacy, current = legacy))
    }

    @Test
    fun anInterruptedCopy_continuesWithWhatIsMissing() {
        val half = legacy.take(2)

        assertEquals(legacy.drop(2).toSet(), planLegacyCopy(legacy, current = half).toSet())
        assertFalse(isLegacyCopyComplete(legacy, current = half))
    }

    @Test
    fun aNewerVersionInTheNewLayout_isNeverOverwritten_anOlderOneIs() {
        val renamedSince = legacy.first().renamed("Rome (other phone)", at = 30)
        val olderExpense = legacy.first { it.uuid == "e1" }.copy(updatedAt = 5)

        val copy = planLegacyCopy(legacy, current = listOf(renamedSince, olderExpense))

        assertFalse(copy.any { it.kind == SyncKind.TRIP })
        assertTrue(copy.any { it.uuid == "e1" && it.updatedAt == 10L })
    }

    @Test
    fun aTripDeletedInTheNewLayoutSince_getsNoChildrenCopied() {
        val deletedSince = legacy.first().deletedAt(30)

        assertTrue(planLegacyCopy(legacy, current = listOf(deletedSince)).isEmpty())
        assertTrue(isLegacyCopyComplete(legacy, current = listOf(deletedSince)))
    }

    @Test
    fun aLegacyTripTombstone_isCopied_butNotItsChildren() {
        val deleted = convertLegacy(
            listOf(legacyTrip("t1", 10).deletedAt(40), expense("e1", "t1", 10).deletedAt(40)), uid = "alice",
        )

        assertEquals(listOf(SyncKind.TRIP), planLegacyCopy(deleted, current = emptyList()).map { it.kind })
    }

    @Test
    fun aChildTombstoneOfALiveTrip_isCopied_soAnOlderEditCanNotBringItBack() {
        val withDelete = legacy + expense("e2", "t1", 10).deletedAt(50)

        assertTrue(planLegacyCopy(withDelete, current = legacy).single().let { it.uuid == "e2" && it.isDeleted })
    }
}
