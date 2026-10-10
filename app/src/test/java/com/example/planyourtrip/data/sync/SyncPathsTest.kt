package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.sync.SyncTestData.activity
import com.example.planyourtrip.data.sync.SyncTestData.budget
import com.example.planyourtrip.data.sync.SyncTestData.checklistItem
import com.example.planyourtrip.data.sync.SyncTestData.deletedAt
import com.example.planyourtrip.data.sync.SyncTestData.expense
import com.example.planyourtrip.data.sync.SyncTestData.settlement
import com.example.planyourtrip.data.sync.SyncTestData.traveler
import com.example.planyourtrip.data.sync.SyncTestData.trip
import com.example.planyourtrip.data.trip.ChecklistKind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every sync kind, written by a member to its document ([SyncPaths.of]) and read back the way FirestoreCloudStore
 * reads it (that document's own id + the trip it belongs to, [SyncDocuments.fromDocument]), is the same item: same
 * kind, uuid and trip. The budget on 2026-10-09 came back under the member's uid; this test would have caught it.
 */
class SyncPathsTest {

    private val member = "bob"

    private val packing = checklistItem("p1", "t1", updatedAt = 3).let {
        it.copy(kind = SyncKind.PACKING, data = (it.data as ItemData.ChecklistData).copy(list = ChecklistKind.PACKING))
    }

    /** One item of every kind, with the document Firestore keeps it in. */
    private val samples: Map<SyncKind, Pair<SyncItem, String>> = mapOf(
        SyncKind.TRIP to (trip("t1", updatedAt = 3, owner = "alice") to "trips/t1"),
        SyncKind.TRAVELER to (traveler("r1", "t1", updatedAt = 3) to "trips/t1/travelers/r1"),
        SyncKind.EXPENSE to (expense("e1", "t1", updatedAt = 3, paidBy = member) to "trips/t1/expenses/e1"),
        SyncKind.ACTIVITY to (activity("a1", "t1", updatedAt = 3) to "trips/t1/activities/a1"),
        SyncKind.CHECKLIST to (checklistItem("c1", "t1", updatedAt = 3) to "trips/t1/checklist/c1"),
        SyncKind.SETTLEMENT to (settlement("s1", "t1", updatedAt = 3) to "trips/t1/settlements/s1"),
        SyncKind.BUDGET to (budget("t1", updatedAt = 3) to "trips/t1/memberData/bob"),
        SyncKind.PACKING to (packing to "trips/t1/memberData/bob/packing/p1"),
    )

    private fun readBack(item: SyncItem): SyncItem? {
        val path = SyncPaths.of(member, item)
        return SyncDocuments.fromDocument(
            item.kind, SyncPaths.documentIdOf(path), SyncPaths.tripUuidOf(path), SyncDocuments.toDocument(item),
        )
    }

    @Test
    fun everyKind_isCovered() {
        assertEquals(SyncKind.entries.toSet(), samples.keys)
    }

    @Test
    fun everyKind_isWrittenToItsFirestoreDocument() {
        for ((kind, sample) in samples) {
            val (item, path) = sample
            assertEquals(kind.name, path, SyncPaths.of(member, item).joinToString("/"))
        }
    }

    @Test
    fun everyKind_readBackFromItsDocument_hasTheSameKindUuidAndTrip() {
        for ((item, _) in samples.values) {
            val read = checkNotNull(readBack(item)) { "${item.kind} unreadable" }
            assertEquals(item.kind.name, Triple(item.kind, item.uuid, item.tripUuid), Triple(read.kind, read.uuid, read.tripUuid))
            assertEquals(item.kind.name, item, read)
        }
    }

    @Test
    fun everyKindsTombstone_readBackFromItsDocument_isTheSameTombstone() {
        for ((item, _) in samples.values) {
            val tombstone = item.deletedAt(9)
            assertEquals(item.kind.name, tombstone, readBack(tombstone))
        }
    }
}
