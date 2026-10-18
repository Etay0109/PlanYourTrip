package com.example.planyourtrip.data.sync

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeCloudStore
import com.example.planyourtrip.FakeNetworkMonitor
import com.example.planyourtrip.FakeSyncFlags
import com.example.planyourtrip.FakeSyncLocalStore
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.sync.SyncTestData.trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The full sync follows the app's foreground: none while Android runs the process in the background only (boot, an
 * alarm, WorkManager), one each time the app comes to the foreground. Quick syncs of local changes are not held back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncForegroundTest {

    private val alice = AuthUser(uid = "alice", displayName = "Alice", email = "alice@example.com")

    private val auth = FakeAuthRepository(alice)
    private val cloud = FakeCloudStore()
    private val local = FakeSyncLocalStore()
    private val network = FakeNetworkMonitor(online = true)
    private val foreground = MutableStateFlow(false)

    private fun TestScope.manager() = SyncManager(
        authRepository = auth,
        cloud = cloud,
        local = local,
        network = network,
        flags = FakeSyncFlags(),
        tripCleanup = {},
        scope = backgroundScope,
        foreground = foreground,
    )

    /** Runs the manager's background work, including the quick sync's short wait. */
    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    private fun fullSyncs() = cloud.calls.count { it == "fetchAll:alice" }

    @Test
    fun startedInTheBackground_noFullSync_untilTheAppComesToTheForeground() = runTest {
        cloud.put("alice", trip("t1", updatedAt = 100))
        manager().start()
        settle()
        assertEquals(0, fullSyncs())
        assertEquals(emptySet<String>(), local.liveOf("alice").keys)

        foreground.value = true
        settle()

        assertEquals(1, fullSyncs())
        assertEquals(setOf("t1"), local.liveOf("alice").keys)
    }

    @Test
    fun everyReturnToTheForeground_runsAFullSync() = runTest {
        foreground.value = true
        manager().start()
        settle()
        assertEquals(1, fullSyncs())

        foreground.value = false
        settle()
        assertEquals(1, fullSyncs())

        foreground.value = true
        settle()
        assertEquals(2, fullSyncs())
    }

    @Test
    fun reconnectInTheBackground_waitsForTheForeground() = runTest {
        foreground.value = true
        manager().start()
        settle()
        foreground.value = false
        settle()

        network.isOnline.value = false
        settle()
        network.isOnline.value = true
        settle()
        assertEquals(1, fullSyncs())

        foreground.value = true
        settle()
        assertEquals(2, fullSyncs())
    }

    @Test
    fun reconnectInTheForeground_runsAFullSync() = runTest {
        foreground.value = true
        manager().start()
        settle()

        network.isOnline.value = false
        settle()
        network.isOnline.value = true
        settle()

        assertEquals(2, fullSyncs())
    }

    @Test
    fun localChangeInTheBackground_isStillUploaded() = runTest {
        val sync = manager()
        sync.start()
        settle()

        local.edit("alice", trip("t1", updatedAt = 100))
        sync.onLocalChange()
        settle()

        assertEquals(setOf("t1"), cloud.docsOf("alice").keys)
        assertEquals(0, fullSyncs())
    }

    @Test
    fun signInWhileInTheForeground_runsAFullSync() = runTest {
        auth.currentUser.value = null
        foreground.value = true
        manager().start()
        settle()
        assertEquals(0, fullSyncs())

        auth.currentUser.value = alice
        settle()

        assertEquals(1, fullSyncs())
    }
}
