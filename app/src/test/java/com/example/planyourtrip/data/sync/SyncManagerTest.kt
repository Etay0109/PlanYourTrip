package com.example.planyourtrip.data.sync

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeCloudStore
import com.example.planyourtrip.FakeNetworkMonitor
import com.example.planyourtrip.FakeSyncFlags
import com.example.planyourtrip.FakeSyncLocalStore
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.sync.SyncTestData.activity
import com.example.planyourtrip.data.sync.SyncTestData.budget
import com.example.planyourtrip.data.sync.SyncTestData.checklistItem
import com.example.planyourtrip.data.sync.SyncTestData.deletedAt
import com.example.planyourtrip.data.sync.SyncTestData.expense
import com.example.planyourtrip.data.sync.SyncTestData.renamed
import com.example.planyourtrip.data.sync.SyncTestData.settlement
import com.example.planyourtrip.data.sync.SyncTestData.traveler
import com.example.planyourtrip.data.sync.SyncTestData.trip
import com.example.planyourtrip.data.trip.ChecklistKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private val alice = AuthUser(uid = "alice", displayName = "Alice", email = "alice@example.com")
    private val bob = AuthUser(uid = "bob", displayName = "Bob", email = "bob@example.com")

    private val auth = FakeAuthRepository(alice)
    private val cloud = FakeCloudStore()
    private val local = FakeSyncLocalStore()
    private val network = FakeNetworkMonitor(online = true)
    private val flags = FakeSyncFlags()

    /** Accounts whose documents of deleted trips were cleaned up (after each full sync). */
    private val cleanups = mutableListOf<String>()

    private val rome = trip("t1", updatedAt = 100)
    private val dinner = expense("e1", "t1", updatedAt = 100)
    private val colosseum = activity("a1", "t1", updatedAt = 100)

    private fun TestScope.manager(chunkSize: Int = 100) = SyncManager(
        authRepository = auth,
        cloud = cloud,
        local = local,
        network = network,
        flags = flags,
        tripCleanup = { cleanups += it },
        scope = backgroundScope,
        uploadChunkSize = chunkSize,
    )

    /**
     * Runs the manager's work in backgroundScope (advanceUntilIdle stops when only background work is left),
     * including the quick sync's short wait.
     */
    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    private fun pendingOf(uid: String) = local.rowsOf(uid).count { it.pending }

    @Test
    fun firstSync_uploadsTheExistingData_andEverythingIsSaved() = runTest {
        listOf(rome, dinner, colosseum).forEach { local.edit("alice", it) }
        val sync = manager()

        assertTrue(sync.sync("alice", full = true))
        settle()

        assertEquals(setOf(rome, dinner, colosseum), cloud.docsOf("alice").values.toSet())
        assertEquals(0, pendingOf("alice"))
        assertEquals(SyncStatus.SAVED, sync.status.value)
    }

    @Test
    fun signIn_downloadsTheAccountsData() = runTest {
        cloud.put("alice", rome, dinner, colosseum)
        manager().start()

        settle()

        assertEquals(setOf(rome, dinner, colosseum), local.liveOf("alice").values.toSet())
        assertEquals(0, pendingOf("alice"))
    }

    @Test
    fun editedOnTwoDevices_theNewerChangeEndsUpOnBoth() = runTest {
        cloud.put("alice", rome.renamed("Rome (other phone)", at = 300))
        local.edit("alice", rome.renamed("Rome (here)", at = 200))

        manager().sync("alice", full = true)

        assertEquals(rome.renamed("Rome (other phone)", at = 300), local.liveOf("alice")["t1"])
        assertEquals(0, pendingOf("alice"))
    }

    @Test
    fun deletedHere_editedOnAnotherDeviceEarlier_theDeleteReachesTheCloud() = runTest {
        cloud.put("alice", rome, dinner.copy(updatedAt = 200))
        local.edit("alice", rome, pending = false)
        local.edit("alice", dinner, pending = false)
        local.delete("alice", dinner, at = 300)

        manager().sync("alice", full = true)

        assertEquals(dinner.deletedAt(300), cloud.docsOf("alice")["e1"])
        assertNull(local.rows.value["e1"])
    }

    @Test
    fun secondAccountOnTheSameDevice_neverReadsOrWritesTheFirstAccountsData() = runTest {
        // Alice signed out offline with "Sign out anyway": her pending trip stays on the device.
        local.edit("alice", rome)
        auth.currentUser.value = bob
        cloud.put("bob", trip("t9", updatedAt = 50, destination = "Oslo"))
        manager().start()

        settle()

        assertEquals(setOf("t9"), local.liveOf("bob").keys)
        assertEquals(setOf("t1"), local.liveOf("alice").keys)
        assertEquals(1, pendingOf("alice")) // still waiting for Alice
        assertTrue(cloud.docsOf("alice").isEmpty())
        assertEquals(setOf("t9"), cloud.docsOf("bob").keys)
        assertFalse(cloud.calls.any { it.contains("alice") })
    }

    @Test
    fun goingOfflineInTheMiddleOfASync_losesNothing_andTheNextSyncFinishesTheJob() = runTest {
        listOf(rome, dinner, colosseum).forEach { local.edit("alice", it) }
        val sync = manager(chunkSize = 1)
        cloud.failAfterWrites = 1 // the first item gets through, then the connection drops

        assertFalse(sync.sync("alice", full = true))
        settle()

        assertEquals(1, cloud.docsOf("alice").size)
        assertEquals(2, pendingOf("alice"))
        assertEquals(setOf(rome, dinner, colosseum), local.liveOf("alice").values.toSet()) // nothing lost
        assertEquals(SyncStatus.OFFLINE, sync.status.value)

        cloud.failAfterWrites = null
        assertTrue(sync.sync("alice", full = true))
        settle()

        assertEquals(setOf(rome, dinner, colosseum), cloud.docsOf("alice").values.toSet())
        assertEquals(3, local.liveOf("alice").size) // no duplicates
        assertEquals(0, pendingOf("alice"))
        assertEquals(SyncStatus.SAVED, sync.status.value)
    }

    @Test
    fun offlineDownload_keepsEverythingPending() = runTest {
        local.edit("alice", rome)
        cloud.offline = true
        val sync = manager()

        assertFalse(sync.sync("alice", full = true))
        settle()

        assertEquals(1, pendingOf("alice"))
        assertEquals(SyncStatus.OFFLINE, sync.status.value)
    }

    @Test
    fun noNetwork_doesNotCallTheCloud_andShowsOffline() = runTest {
        network.isOnline.value = false
        local.edit("alice", rome)
        val sync = manager()

        assertFalse(sync.sync("alice", full = false))
        settle()

        assertTrue(cloud.calls.isEmpty())
        assertEquals(SyncStatus.OFFLINE, sync.status.value)
    }

    @Test
    fun whenTheConnectionReturns_pendingChangesAreUploaded() = runTest {
        network.isOnline.value = false
        manager().start()
        settle()
        local.edit("alice", rome)

        network.isOnline.value = true
        settle()

        assertEquals(rome, cloud.docsOf("alice")["t1"])
        assertEquals(0, pendingOf("alice"))
    }

    @Test
    fun aTripDeletedOffline_isRemovedFromTheCloudWithItsChildren_whenTheConnectionReturns() = runTest {
        val passport = checklistItem("c1", "t1", updatedAt = 100)
        cloud.put("alice", rome, dinner, colosseum, passport)
        val sync = manager()
        sync.start()
        settle()
        assertEquals(setOf("t1", "e1", "a1", "c1"), local.liveOf("alice").keys)

        // Offline: the trip is deleted here (its children go with it) and the delete waits.
        network.isOnline.value = false
        settle()
        cloud.calls.clear()
        local.delete("alice", rome, at = 500)
        sync.onLocalChange()
        settle()

        assertTrue(cloud.calls.isEmpty())
        assertEquals(SyncStatus.OFFLINE, sync.status.value)
        assertTrue(local.liveOf("alice").isEmpty())
        assertEquals(1, pendingOf("alice")) // the trip's tombstone
        assertEquals(rome, cloud.docsOf("alice")["t1"])

        network.isOnline.value = true
        settle()

        val docs = cloud.docsOf("alice")
        assertEquals(rome.deletedAt(500), docs["t1"])
        assertEquals(dinner.deletedAt(500), docs["e1"])
        assertEquals(colosseum.deletedAt(500), docs["a1"])
        assertEquals(passport.deletedAt(500), docs["c1"])
        assertTrue(local.rowsOf("alice").isEmpty()) // tombstone uploaded and dropped, nothing came back
        assertEquals(SyncStatus.SAVED, sync.status.value)
    }

    @Test
    fun aLocalChange_isUploadedWithAQuickSync() = runTest {
        cloud.put("alice", rome)
        val sync = manager()
        sync.start()
        settle()
        cloud.calls.clear()

        local.edit("alice", dinner)
        sync.onLocalChange()
        settle()

        assertEquals(dinner, cloud.docsOf("alice")["e1"])
        assertEquals(listOf("fetch:alice:1", "write:alice:1"), cloud.calls) // only the changed item
        assertEquals(0, pendingOf("alice"))
    }

    @Test
    fun anEditMadeWhileItsUploadRuns_staysPending() = runTest {
        local.edit("alice", rome)
        val newer = rome.renamed("Rome again", at = 500)
        cloud.onWrite = { local.edit("alice", newer) }

        manager().sync("alice", full = false)

        assertEquals(1, pendingOf("alice"))
        assertEquals(newer, local.liveOf("alice")["t1"])
    }

    // ---------- Sign out ----------

    @Test
    fun signOut_withEverythingUploaded_removesTheAccountsDataFromTheDevice() = runTest {
        local.edit("alice", rome, pending = false)
        local.edit("bob", trip("t9", updatedAt = 50), pending = false)

        val result = manager().signOut(force = false)

        assertEquals(SignOutResult.SIGNED_OUT, result)
        assertNull(auth.currentUser.value)
        assertTrue(local.rowsOf("alice").isEmpty())
        assertEquals(1, local.rowsOf("bob").size) // other accounts untouched
    }

    @Test
    fun signOut_uploadsPendingChangesFirst() = runTest {
        local.edit("alice", rome)

        val result = manager().signOut(force = false)

        assertEquals(SignOutResult.SIGNED_OUT, result)
        assertEquals(rome, cloud.docsOf("alice")["t1"])
        assertTrue(local.rowsOf("alice").isEmpty())
    }

    @Test
    fun signOut_withPendingChangesAndNoConnection_asksFirst() = runTest {
        local.edit("alice", rome)
        network.isOnline.value = false

        val result = manager().signOut(force = false)

        assertEquals(SignOutResult.NEEDS_CONFIRMATION, result)
        assertEquals(alice, auth.currentUser.value)
        assertEquals(1, pendingOf("alice"))
    }

    @Test
    fun signOutAnyway_keepsTheAccountsDataOnTheDevice() = runTest {
        local.edit("alice", rome)
        network.isOnline.value = false

        val result = manager().signOut(force = true)

        assertEquals(SignOutResult.SIGNED_OUT, result)
        assertNull(auth.currentUser.value)
        assertEquals(1, pendingOf("alice"))
    }

    // ---------- The legacy copy (users/{uid}/trips, before shared trips) ----------

    /** Alice's legacy copy as the old app stored it: the key "me", the budget in the trip, both checklists together. */
    private fun legacyCopy(): List<SyncItem> {
        val trip = rome.copy(data = (rome.data as ItemData.TripData).copy(ownerId = "", memberIds = emptyList()))
        val shared = dinner.copy(
            data = (dinner.data as ItemData.ExpenseData).copy(paidBy = "me", splitBetween = listOf("me", "r1")),
        )
        val packing = checklistItem("c2", "t1", updatedAt = 100, text = "Charger").let {
            it.copy(data = (it.data as ItemData.ChecklistData).copy(list = ChecklistKind.PACKING))
        }
        return listOf(
            trip, budget("t1", updatedAt = 100), shared, colosseum, traveler("r1", "t1", updatedAt = 100),
            settlement("s1", "t1", updatedAt = 100, from = "r1", to = "me"),
            checklistItem("c1", "t1", updatedAt = 100), packing,
        )
    }

    /** The same data on this phone after the v14 Room migration ("me" is Alice's uid, synced). */
    private fun migratedLocally(): List<SyncItem> = convertLegacy(legacyCopy(), "alice")

    @Test
    fun theLegacyCopy_isMovedToSharedTrips_withTheSameData_andOnlyThenDeleted() = runTest {
        cloud.legacy["alice"] = legacyCopy().toMutableList()
        migratedLocally().forEach { local.edit("alice", it, pending = false) }

        assertTrue(manager().sync("alice", full = true))

        val docs = cloud.docsOf("alice")
        assertEquals(listOf("alice"), cloud.membersOf("t1"))
        assertEquals(migratedLocally().toSet(), docs.values.toSet())
        val expense = docs.getValue("e1").data as ItemData.ExpenseData
        assertEquals("alice", expense.paidBy)
        assertEquals(listOf("alice", "r1"), expense.splitBetween)
        assertEquals("alice", (docs.getValue("s1").data as ItemData.SettlementData).toKey)
        assertEquals(SyncKind.PACKING, docs.getValue("c2").kind)
        assertEquals(600_000L, (docs.getValue("budget:t1").data as ItemData.BudgetData).budgetAgorot)
        // Copied, checked, then marked and deleted; nothing on the phone changed.
        assertNull(cloud.legacy["alice"])
        assertTrue("alice" in cloud.legacyMigrated)
        assertTrue("alice" in flags.sharedTripsMigrated)
        assertEquals(migratedLocally().map { it.uuid to it.kind }.toSet(), local.rowsOf("alice").map { it.item.uuid to it.item.kind }.toSet())
        assertEquals(0, pendingOf("alice"))
    }

    @Test
    fun anInterruptedCopy_deletesNothing_keepsThePhonesTrips_andTheNextSyncFinishes() = runTest {
        cloud.legacy["alice"] = legacyCopy().toMutableList()
        migratedLocally().forEach { local.edit("alice", it, pending = false) }
        cloud.failAfterWrites = 2 // the connection drops after two of the copy's chunks

        assertFalse(manager(chunkSize = 2).sync("alice", full = true))

        // The legacy copy is untouched and the half-copied new layout never made the phone delete anything.
        assertEquals(legacyCopy(), cloud.legacy["alice"])
        assertFalse("alice" in cloud.legacyMigrated)
        assertFalse("alice" in flags.sharedTripsMigrated)
        assertEquals(migratedLocally().size, local.rowsOf("alice").size)

        cloud.failAfterWrites = null
        assertTrue(manager(chunkSize = 2).sync("alice", full = true))
        assertEquals(migratedLocally().toSet(), cloud.docsOf("alice").values.toSet())
        assertNull(cloud.legacy["alice"])
        assertEquals(migratedLocally().size, local.rowsOf("alice").size)
    }

    @Test
    fun interruptedAfterTheMarker_theNextSyncOnlyDeletesTheLeftovers() = runTest {
        cloud.legacy["alice"] = legacyCopy().toMutableList()
        cloud.failLegacyDelete = true

        assertFalse(manager().sync("alice", full = true))
        assertTrue("alice" in cloud.legacyMigrated)
        assertEquals(legacyCopy(), cloud.legacy["alice"])
        assertFalse("alice" in flags.sharedTripsMigrated)

        cloud.failLegacyDelete = false
        cloud.calls.clear()
        assertTrue(manager().sync("alice", full = true))
        assertNull(cloud.legacy["alice"])
        assertTrue("alice" in flags.sharedTripsMigrated)
        // Everything was already copied: nothing is written again.
        assertFalse(cloud.calls.any { it.startsWith("write:") })
    }

    @Test
    fun theMigration_neverOverwritesANewerEditFromAnUpdatedPhone() = runTest {
        // Another phone migrated first and renamed the trip since.
        cloud.put("alice", rome.renamed("Rome (other phone)", at = 300))
        cloud.legacy["alice"] = legacyCopy().toMutableList()

        assertTrue(manager().sync("alice", full = true))

        assertEquals("Rome (other phone)", (cloud.docsOf("alice").getValue("t1").data as ItemData.TripData).destination)
        assertEquals("Rome (other phone)", (local.liveOf("alice").getValue("t1").data as ItemData.TripData).destination)
    }

    @Test
    fun aQuickSync_movesTheLegacyCopyFirst() = runTest {
        cloud.legacy["alice"] = legacyCopy().toMutableList()
        local.edit("alice", colosseum.copy(updatedAt = 200))

        assertTrue(manager().sync("alice", full = false))

        assertEquals(listOf("fetchLegacy:alice"), cloud.calls.take(1))
        assertNull(cloud.legacy["alice"])
        assertEquals(colosseum.copy(updatedAt = 200), cloud.docsOf("alice")["a1"])
    }

    @Test
    fun eachAccount_movesOnlyItsOwnLegacyCopy() = runTest {
        cloud.legacy["alice"] = legacyCopy().toMutableList()
        cloud.legacy["bob"] = mutableListOf(trip("t9", updatedAt = 50, destination = "Oslo", owner = ""))

        assertTrue(manager().sync("bob", full = true))

        assertEquals(legacyCopy(), cloud.legacy["alice"])
        assertNull(cloud.legacy["bob"])
        assertEquals(listOf("bob"), cloud.membersOf("t9"))
        assertNull(cloud.membersOf("t1"))
        assertFalse(cloud.calls.any { it.contains("alice") })
    }

    @Test
    fun anAccountWithoutLegacyData_isMarkedOnce() = runTest {
        val sync = manager()
        sync.start()
        settle()
        network.isOnline.value = false
        network.isOnline.value = true
        settle()

        assertEquals(1, cloud.calls.count { it == "fetchLegacy:alice" })
        assertTrue("alice" in flags.sharedTripsMigrated)
    }

    // ---------- Shared trips: personal data stays personal ----------

    @Test
    fun theBudgetAndPackingItems_areOnlySeenByTheirMember_andMembersSeeTheSharedItems() = runTest {
        val packing = checklistItem("c2", "t1", updatedAt = 100, text = "Charger").let {
            it.copy(kind = SyncKind.PACKING, data = (it.data as ItemData.ChecklistData).copy(list = ChecklistKind.PACKING))
        }
        listOf(rome, budget("t1", updatedAt = 100), dinner, checklistItem("c1", "t1", updatedAt = 100), packing)
            .forEach { local.edit("alice", it) }
        assertTrue(manager().sync("alice", full = true))
        cloud.addMember("t1", "bob")

        val bobSees = cloud.docsOf("bob")
        assertEquals(setOf("t1", "e1", "c1"), bobSees.keys)
        assertEquals(setOf("t1", "budget:t1", "e1", "c1", "c2"), cloud.docsOf("alice").keys)
    }

    @Test
    fun uploadingATrip_neverChangesItsMembers() = runTest {
        local.edit("alice", rome)
        val sync = manager()
        assertTrue(sync.sync("alice", full = true))
        cloud.addMember("t1", "bob")

        // Alice's phone still has the old member list and edits the trip.
        local.edit("alice", rome.renamed("Rome and Naples", at = 200))
        assertTrue(sync.sync("alice", full = false))

        assertEquals(listOf("alice", "bob"), cloud.membersOf("t1"))
        assertTrue(sync.sync("alice", full = true))
        val here = local.liveOf("alice").getValue("t1").data as ItemData.TripData
        assertEquals("Rome and Naples", here.destination)
        assertEquals(listOf("alice", "bob"), here.memberIds)
    }

    @Test
    fun refresh_isAFullSync_andFailsOffline() = runTest {
        cloud.put("alice", rome)
        val sync = manager()

        assertTrue(sync.refresh())
        assertEquals(setOf("t1"), local.liveOf("alice").keys)

        network.isOnline.value = false
        assertFalse(sync.refresh())
    }

    // ---------- Checklist items and the documents of deleted trips ----------

    @Test
    fun checklistItems_uploadAndDownloadLikeTheOtherItems() = runTest {
        val passport = checklistItem("c1", "t1", updatedAt = 100)
        local.edit("alice", rome)
        local.edit("alice", passport)
        // In shared trips a child item is always stored under its trip.
        cloud.put("alice", rome, checklistItem("c2", "t1", updatedAt = 50, text = "Charger"))

        assertTrue(manager().sync("alice", full = true))

        assertEquals(passport, cloud.docsOf("alice")["c1"])
        assertEquals("Charger", (local.liveOf("alice")["c2"]?.data as ItemData.ChecklistData).text)
        assertEquals(0, pendingOf("alice"))
    }

    @Test
    fun travelersSettlementsAndSharedExpenses_uploadAndDownloadLikeTheOtherItems() = runTest {
        val noa = traveler("r1", "t1", updatedAt = 100, name = "Noa")
        val shared = dinner.copy(
            updatedAt = 110,
            data = (dinner.data as ItemData.ExpenseData).copy(paidBy = "r1", splitBetween = listOf("alice", "r1")),
        )
        local.edit("alice", rome)
        local.edit("alice", noa)
        local.edit("alice", shared)
        // In shared trips a child item is always stored under its trip.
        cloud.put("alice", rome, settlement("s1", "t1", updatedAt = 50, from = "alice", to = "r1"))

        assertTrue(manager().sync("alice", full = true))

        assertEquals(noa, cloud.docsOf("alice")["r1"])
        assertEquals(shared, cloud.docsOf("alice")["e1"])
        assertEquals("alice", (local.liveOf("alice")["s1"]?.data as ItemData.SettlementData).fromKey)
        assertEquals(0, pendingOf("alice"))
    }

    @Test
    fun aDeletedTrip_tombstonesItsTravelersAndSettlementsInTheCloud() = runTest {
        val noa = traveler("r1", "t1", updatedAt = 100)
        val paid = settlement("s1", "t1", updatedAt = 100)
        cloud.put("alice", rome, noa, paid)
        val sync = manager()
        assertTrue(sync.sync("alice", full = true))
        assertEquals(setOf("t1", "r1", "s1"), local.liveOf("alice").keys)

        local.delete("alice", rome, at = 500)
        assertTrue(sync.sync("alice", full = false))

        val docs = cloud.docsOf("alice")
        assertEquals(noa.deletedAt(500), docs["r1"])
        assertEquals(paid.deletedAt(500), docs["s1"])
        assertTrue(local.rowsOf("alice").isEmpty())
    }

    @Test
    fun aFullSync_cleansUpTheDocumentsOfDeletedTrips() = runTest {
        cloud.put("alice", rome)
        assertTrue(manager().sync("alice", full = true))
        assertEquals(listOf("alice"), cleanups)
    }

    @Test
    fun aQuickSync_aFailedOrOfflineSync_andSignOut_neverCleanUpDocuments() = runTest {
        local.edit("alice", rome)
        assertTrue(manager().sync("alice", full = false))

        cloud.offline = true
        assertFalse(manager().sync("alice", full = true))
        cloud.offline = false

        network.isOnline.value = false
        assertFalse(manager().sync("alice", full = true))
        network.isOnline.value = true

        assertEquals(SignOutResult.SIGNED_OUT, manager().signOut(force = false))
        assertTrue(cleanups.isEmpty())
    }
}
