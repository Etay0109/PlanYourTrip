package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.sync.SyncTestData.activity
import com.example.planyourtrip.data.sync.SyncTestData.checklistItem
import com.example.planyourtrip.data.sync.SyncTestData.deletedAt
import com.example.planyourtrip.data.sync.SyncTestData.expense
import com.example.planyourtrip.data.sync.SyncTestData.pending
import com.example.planyourtrip.data.sync.SyncTestData.renamed
import com.example.planyourtrip.data.sync.SyncTestData.synced
import com.example.planyourtrip.data.sync.SyncTestData.trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private val rome = trip("t1", updatedAt = 100)

    // ---------- Changed on two devices ----------

    @Test
    fun editedOnTwoDevices_newerLocalEditWins_andIsUploaded() {
        val mine = rome.renamed("Rome (here)", at = 300)
        val theirs = rome.renamed("Rome (other phone)", at = 200)

        val plan = planMerge(listOf(pending(mine)), listOf(theirs))

        assertEquals(listOf(mine), plan.upload)
        assertTrue(plan.saveLocally.isEmpty())
    }

    @Test
    fun editedOnTwoDevices_newerCloudEditWins_andReplacesThePendingLocalOne() {
        val mine = rome.renamed("Rome (here)", at = 200)
        val theirs = rome.renamed("Rome (other phone)", at = 300)

        val plan = planMerge(listOf(pending(mine)), listOf(theirs))

        assertEquals(listOf(theirs), plan.saveLocally)
        assertTrue(plan.upload.isEmpty())
    }

    @Test
    fun editedOnTwoDevices_atTheSameMillisecond_theCloudWins() {
        val mine = rome.renamed("A", at = 200)
        val theirs = rome.renamed("B", at = 200)

        assertEquals(listOf(theirs), planMerge(listOf(pending(mine)), listOf(theirs)).saveLocally)
    }

    @Test
    fun sameVersionOnBothSides_doesNothing() {
        assertEquals(MergePlan(), planMerge(listOf(synced(rome)), listOf(rome)))
    }

    @Test
    fun uploadedButNotMarkedSynced_isMarkedSyncedBySavingTheCloudCopy() {
        // E.g. the app stopped between the write and markSynced.
        val plan = planMerge(listOf(pending(rome)), listOf(rome))

        assertEquals(listOf(rome), plan.saveLocally)
        assertTrue(plan.upload.isEmpty())
    }

    // ---------- Deleted on one device, edited on another ----------

    @Test
    fun deletedHere_thenEditedElsewhereLater_theEditWins_andTheItemComesBack() {
        val dinner = expense("e1", "t1", updatedAt = 100)
        val deletedHere = dinner.deletedAt(200)
        val editedThere = dinner.copy(updatedAt = 300)

        val plan = planMerge(listOf(synced(rome), pending(deletedHere)), listOf(rome, editedThere))

        assertEquals(listOf(editedThere), plan.saveLocally)
        assertEquals(listOf(deletedHere), plan.dropTombstones)
        assertTrue(plan.upload.isEmpty())
    }

    @Test
    fun deletedHere_afterTheEditElsewhere_theDeleteWins_andIsUploaded() {
        val dinner = expense("e1", "t1", updatedAt = 100)
        val deletedHere = dinner.deletedAt(300)
        val editedThere = dinner.copy(updatedAt = 200)

        val plan = planMerge(listOf(synced(rome), pending(deletedHere)), listOf(rome, editedThere))

        assertEquals(listOf(deletedHere), plan.upload)
        assertTrue(plan.saveLocally.isEmpty())
    }

    @Test
    fun deletedElsewhere_afterTheEditHere_isDeletedHere() {
        val dinner = expense("e1", "t1", updatedAt = 100)
        val editedHere = dinner.copy(updatedAt = 200)
        val deletedThere = dinner.deletedAt(300)

        val plan = planMerge(listOf(synced(rome), pending(editedHere)), listOf(rome, deletedThere))

        assertEquals(listOf(editedHere), plan.deleteLocally)
        assertTrue(plan.upload.isEmpty())
    }

    @Test
    fun deletedElsewhere_beforeTheEditHere_theEditIsUploaded_andRestoresIt() {
        val dinner = expense("e1", "t1", updatedAt = 100)
        val editedHere = dinner.copy(updatedAt = 300)
        val deletedThere = dinner.deletedAt(200)

        val plan = planMerge(listOf(synced(rome), pending(editedHere)), listOf(rome, deletedThere))

        assertEquals(listOf(editedHere), plan.upload)
        assertTrue(plan.deleteLocally.isEmpty())
    }

    @Test
    fun deletedOnBothDevices_dropsTheLocalTombstone() {
        val dinner = expense("e1", "t1", updatedAt = 100)
        val plan = planMerge(listOf(synced(rome), pending(dinner.deletedAt(200))), listOf(rome, dinner.deletedAt(250)))

        assertEquals(listOf(dinner.deletedAt(200)), plan.dropTombstones)
        assertTrue(plan.upload.isEmpty())
    }

    @Test
    fun createdAndDeletedOffline_neverUploaded_justDropsTheTombstone() {
        val dinner = expense("e1", "t1", updatedAt = 100).deletedAt(150)

        val plan = planMerge(listOf(synced(rome), pending(dinner)), listOf(rome))

        assertEquals(listOf(dinner), plan.dropTombstones)
        assertTrue(plan.upload.isEmpty())
    }

    // ---------- First upload, downloads, removed items ----------

    @Test
    fun firstSyncAfterTheUpdate_uploadsAllExistingData() {
        val dinner = expense("e1", "t1", updatedAt = 100)
        val colosseum = activity("a1", "t1", updatedAt = 100)
        val lisbon = trip("t2", updatedAt = 100, destination = "Lisbon")
        val local = listOf(rome, dinner, colosseum, lisbon).map(::pending)

        val plan = planMerge(local, cloud = emptyList())

        assertEquals(setOf(rome, dinner, colosseum, lisbon), plan.upload.toSet())
        assertTrue(plan.saveLocally.isEmpty() && plan.deleteLocally.isEmpty())
    }

    @Test
    fun newDevice_downloadsEverything_tripsBeforeTheirChildren() {
        val dinner = expense("e1", "t1", updatedAt = 100)
        val colosseum = activity("a1", "t1", updatedAt = 100)

        val plan = planMerge(local = emptyList(), cloud = listOf(dinner, colosseum, rome))

        assertEquals(rome, plan.saveLocally.first())
        assertEquals(setOf(rome, dinner, colosseum), plan.saveLocally.toSet())
    }

    @Test
    fun cloudTombstoneOfAnItemThisDeviceNeverHad_isIgnored() {
        assertEquals(MergePlan(), planMerge(emptyList(), listOf(rome.deletedAt(200))))
    }

    @Test
    fun syncedItemMissingFromTheCloud_isDeletedHere() {
        val plan = planMerge(listOf(synced(rome)), cloud = emptyList())

        assertEquals(listOf(rome), plan.deleteLocally)
    }

    @Test
    fun childrenOfATripDeletedElsewhere_areDeleted_andNeverUploaded() {
        val dinner = expense("e1", "t1", updatedAt = 100)
        val newActivity = activity("a1", "t1", updatedAt = 150) // made here offline, before the trip's delete

        val plan = planMerge(
            local = listOf(synced(rome), synced(dinner), pending(newActivity)),
            cloud = listOf(rome.deletedAt(200), dinner.deletedAt(200)),
        )

        assertEquals(setOf(rome, dinner, newActivity), plan.deleteLocally.toSet())
        assertTrue(plan.upload.isEmpty())
        assertTrue(plan.saveLocally.isEmpty())
    }

    @Test
    fun checklistItemsOfATripDeletedElsewhere_areDeleted_andNeverUploaded() {
        val passport = checklistItem("c1", "t1", updatedAt = 100)
        val packed = checklistItem("c2", "t1", updatedAt = 250, checked = true) // toggled here after the delete

        val plan = planMerge(
            local = listOf(synced(rome), synced(passport), pending(packed)),
            cloud = listOf(rome.deletedAt(200), passport.deletedAt(200)),
        )

        assertEquals(setOf(rome, passport, packed), plan.deleteLocally.toSet())
        assertTrue(plan.upload.isEmpty())
    }

    @Test
    fun checklistItem_toggledOnTwoDevices_theNewerChangeWins() {
        val here = checklistItem("c1", "t1", updatedAt = 300, checked = true)
        val there = checklistItem("c1", "t1", updatedAt = 200, checked = false)

        assertEquals(listOf(here), planMerge(listOf(pending(here)), listOf(there)).upload)
        assertEquals(listOf(here), planMerge(listOf(synced(there)), listOf(here)).saveLocally)
    }

    @Test
    fun quickSync_withOnlyAPendingChild_doesNotTouchItsTrip() {
        // The trip is not part of a quick sync; its child is uploaded normally.
        val dinner = expense("e1", "t1", updatedAt = 300)

        val plan = planMerge(listOf(pending(dinner)), listOf(expense("e1", "t1", updatedAt = 100)))

        assertEquals(listOf(dinner), plan.upload)
        assertTrue(plan.deleteLocally.isEmpty())
    }

    @Test
    fun newDevice_savesTripsFirst_thenTheirExpensesAndActivities() {
        val visit = activity("a1", "t1", updatedAt = 100)
        val dinner = expense("e1", "t1", updatedAt = 100)

        val passport = checklistItem("c1", "t1", updatedAt = 100)

        val plan = planMerge(local = emptyList(), cloud = listOf(passport, visit, dinner, rome))

        assertEquals(
            listOf(SyncKind.TRIP, SyncKind.EXPENSE, SyncKind.ACTIVITY, SyncKind.CHECKLIST),
            plan.saveLocally.map { it.kind },
        )
    }
}
