package com.example.planyourtrip.data.sync

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeCloudStore
import com.example.planyourtrip.FakeNetworkMonitor
import com.example.planyourtrip.FakeSyncFlags
import com.example.planyourtrip.FakeSyncLocalStore
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.sync.SyncTestData.budget
import com.example.planyourtrip.data.sync.SyncTestData.expense
import com.example.planyourtrip.data.sync.SyncTestData.trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * The status tells the truth about the last full sync ("All changes saved" only after one succeeded, "Sync problem"
 * with the time of the last success), and signing out removes an account's data from the device only when it is
 * safely in the cloud (migrated, last full sync succeeded, nothing pending). Never the real Firestore.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusAndSignOutTest {

    private val alice = AuthUser(uid = "alice", displayName = "Alice", email = "alice@example.com")
    private val bob = AuthUser(uid = "bob", displayName = "Bob", email = "bob@example.com")

    private val auth = FakeAuthRepository(alice)
    private val cloud = FakeCloudStore()
    private val local = FakeSyncLocalStore()
    private val network = FakeNetworkMonitor(online = true)
    private val flags = FakeSyncFlags()
    private val clock = MutableClock(1_000)

    private val rome = trip("t1", updatedAt = 100)
    private val dinner = expense("e1", "t1", updatedAt = 100)

    private fun TestScope.manager() = SyncManager(
        authRepository = auth,
        cloud = cloud,
        local = local,
        network = network,
        flags = flags,
        tripCleanup = TripCleanup.None,
        scope = backgroundScope,
        clock = clock,
    )

    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    /** A legacy copy whose budget can't be read back: the migration can never finish (the 2026-10-09 bug). */
    private fun givenAMigrationThatCanNotFinish() {
        cloud.legacy["alice"] = mutableListOf(rome, budget("t1", updatedAt = 100))
        cloud.unreadableKinds = setOf(SyncKind.BUDGET)
        local.edit("alice", rome, pending = false)
    }

    // ---------- Status ----------

    @Test
    fun nothingPending_butNoFullSyncEverSucceeded_isNeverAllChangesSaved() = runTest {
        givenAMigrationThatCanNotFinish()
        val sync = manager()
        sync.start()
        settle()

        assertEquals(0, local.rowsOf("alice").count { it.pending })
        assertEquals(SyncStatus.PROBLEM, sync.status.value)
        assertNull(sync.lastSyncedAt.value)
    }

    @Test
    fun aRetryThatSucceeds_showsAllChangesSaved_andTheTime() = runTest {
        givenAMigrationThatCanNotFinish()
        val sync = manager()
        sync.start()
        settle()

        cloud.unreadableKinds = emptySet()
        clock.millis = 5_000
        assertTrue(sync.refresh())
        settle()

        assertEquals(SyncStatus.SAVED, sync.status.value)
        assertEquals(5_000L, sync.lastSyncedAt.value)
    }

    @Test
    fun aFailedFullSync_showsTheProblem_withTheTimeOfTheLastSuccess() = runTest {
        cloud.put("alice", rome)
        val sync = manager()
        clock.millis = 2_000
        assertTrue(sync.sync("alice", full = true))
        settle()
        assertEquals(SyncStatus.SAVED, sync.status.value)

        // Something the connection doesn't explain: the rules deny reading my own trip t2.
        local.edit("alice", trip("t2", updatedAt = 300), pending = false)
        cloud.denyTripReads = true
        clock.millis = 9_000
        assertTrue(!sync.sync("alice", full = true))
        settle()

        assertEquals(SyncStatus.PROBLEM, sync.status.value)
        assertEquals(2_000L, sync.lastSyncedAt.value)
    }

    @Test
    fun aQuickSyncThatWorks_doesNotHideAFailedFullSync() = runTest {
        cloud.put("alice", rome)
        local.edit("alice", rome, pending = false)
        local.edit("alice", trip("t2", updatedAt = 300), pending = false)
        flags.sharedTripsMigrated += "alice"
        cloud.denyTripReads = true
        val sync = manager()
        assertTrue(!sync.sync("alice", full = true)) // my own trip t2 can't be read: a sync problem

        local.edit("alice", dinner)
        assertTrue(sync.sync("alice", full = false))
        settle()

        assertEquals(SyncStatus.PROBLEM, sync.status.value)
    }

    @Test
    fun noConnection_isOffline_notASyncProblem() = runTest {
        local.edit("alice", rome, pending = false)
        cloud.offline = true
        val sync = manager()

        assertTrue(!sync.sync("alice", full = true))
        settle()

        assertEquals(SyncStatus.OFFLINE, sync.status.value)
    }

    @Test
    fun theLastSuccess_isRemembered_afterTheAppRestarts() = runTest {
        cloud.put("alice", rome)
        clock.millis = 4_000
        assertTrue(manager().sync("alice", full = true))
        network.isOnline.value = false

        val restarted = manager()
        settle()

        assertEquals(4_000L, restarted.lastSyncedAt.value)
        assertEquals(SyncStatus.SAVED, restarted.status.value)
    }

    @Test
    fun theStatusIsPerAccount() = runTest {
        givenAMigrationThatCanNotFinish()
        val sync = manager()
        sync.start()
        settle()
        assertEquals(SyncStatus.PROBLEM, sync.status.value)

        auth.currentUser.value = bob
        settle()

        assertEquals(SyncStatus.SAVED, sync.status.value)
        assertEquals(1_000L, sync.lastSyncedAt.value)
    }

    // ---------- Sign out ----------

    @Test
    fun signOut_whileTheMigrationCanNotFinish_keepsTheData_andAsks() = runTest {
        givenAMigrationThatCanNotFinish()

        val result = manager().signOut(force = false)

        assertEquals(SignOutResult.NOT_CONFIRMED, result)
        assertEquals(alice, auth.currentUser.value)
        assertEquals(setOf("t1"), local.liveOf("alice").keys)
    }

    @Test
    fun signOut_offline_afterAFailedFullSync_keepsTheData_andAsks() = runTest {
        local.edit("alice", rome, pending = false)
        flags.sharedTripsMigrated += "alice"
        cloud.offline = true
        val sync = manager()
        assertTrue(!sync.sync("alice", full = true))
        network.isOnline.value = false

        assertEquals(SignOutResult.NOT_CONFIRMED, sync.signOut(force = false))
        assertEquals(setOf("t1"), local.liveOf("alice").keys)
    }

    @Test
    fun signOut_offline_afterASuccessfulFullSync_withNothingPending_removesTheData() = runTest {
        cloud.put("alice", rome)
        val sync = manager()
        assertTrue(sync.sync("alice", full = true))
        network.isOnline.value = false

        assertEquals(SignOutResult.SIGNED_OUT, sync.signOut(force = false))
        assertNull(auth.currentUser.value)
        assertTrue(local.rowsOf("alice").isEmpty())
    }

    @Test
    fun signOut_neverMigratedOnThisDevice_isNeverSafeOffline() = runTest {
        local.edit("alice", rome, pending = false)
        // A full sync succeeded once (an older app version recorded it), but the migration flag isn't set.
        flags.recordFullSync("alice", FullSyncRecord(succeeded = true, lastSuccessAt = 500))
        network.isOnline.value = false

        assertEquals(SignOutResult.NOT_CONFIRMED, manager().signOut(force = false))
        assertEquals(setOf("t1"), local.liveOf("alice").keys)
    }

    @Test
    fun signOut_pendingChangesThatCanNotUpload_stillSaysSo() = runTest {
        local.edit("alice", rome)
        network.isOnline.value = false

        assertEquals(SignOutResult.NEEDS_CONFIRMATION, manager().signOut(force = false))
        assertEquals(1, local.rowsOf("alice").count { it.pending })
    }

    @Test
    fun signOutAnyway_afterTheWarning_keepsTheData_hiddenFromTheNextAccount() = runTest {
        givenAMigrationThatCanNotFinish()
        val sync = manager()
        assertEquals(SignOutResult.NOT_CONFIRMED, sync.signOut(force = false))

        assertEquals(SignOutResult.SIGNED_OUT, sync.signOut(force = true))
        auth.currentUser.value = bob
        sync.start()
        settle()

        assertEquals(setOf("t1"), local.liveOf("alice").keys)
        assertTrue(local.liveOf("bob").isEmpty())
        assertTrue(cloud.calls.none { it.startsWith("write:bob") })
    }

    /** A [Clock] the test moves by hand. */
    private class MutableClock(var millis: Long) : Clock() {
        override fun millis(): Long = millis
        override fun instant(): Instant = Instant.ofEpochMilli(millis)
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
}
