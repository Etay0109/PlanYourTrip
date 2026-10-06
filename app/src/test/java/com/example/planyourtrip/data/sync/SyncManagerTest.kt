package com.example.planyourtrip.data.sync

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeCloudStore
import com.example.planyourtrip.FakeNetworkMonitor
import com.example.planyourtrip.FakeSyncLocalStore
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.sync.SyncTestData.activity
import com.example.planyourtrip.data.sync.SyncTestData.deletedAt
import com.example.planyourtrip.data.sync.SyncTestData.expense
import com.example.planyourtrip.data.sync.SyncTestData.renamed
import com.example.planyourtrip.data.sync.SyncTestData.trip
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

    private val rome = trip("t1", updatedAt = 100)
    private val dinner = expense("e1", "t1", updatedAt = 100)
    private val colosseum = activity("a1", "t1", updatedAt = 100)

    private fun TestScope.manager(chunkSize: Int = 100) = SyncManager(
        authRepository = auth,
        cloud = cloud,
        local = local,
        network = network,
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
}
