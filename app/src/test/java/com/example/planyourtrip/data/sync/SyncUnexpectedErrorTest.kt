package com.example.planyourtrip.data.sync

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeCloudStore
import com.example.planyourtrip.FakeNetworkMonitor
import com.example.planyourtrip.FakeSyncFlags
import com.example.planyourtrip.FakeSyncLocalStore
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.sync.SyncTestData.trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A local error that isn't a cloud failure (e.g. the database) ends the sync as a problem, never as a crash. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncUnexpectedErrorTest {

    private val alice = AuthUser(uid = "alice", displayName = "Alice", email = "alice@example.com")
    private val auth = FakeAuthRepository(alice)
    private val cloud = FakeCloudStore()
    private val fake = FakeSyncLocalStore()

    /** Fails [SyncLocalStore.apply] while [failApply] is true. */
    private var failApply = false
    private val local = object : SyncLocalStore by fake {
        override suspend fun apply(ownerId: String, plan: MergePlan) {
            if (failApply) throw IllegalStateException("database is full")
            fake.apply(ownerId, plan)
        }
    }

    private val rome = trip("t1", updatedAt = 100)

    private fun TestScope.manager() = SyncManager(
        authRepository = auth,
        cloud = cloud,
        local = local,
        network = FakeNetworkMonitor(online = true),
        flags = FakeSyncFlags(),
        tripCleanup = {},
        scope = backgroundScope,
    )

    private fun TestScope.settle() {
        advanceTimeBy(5_000)
        runCurrent()
    }

    @Test
    fun unexpectedError_failsTheSync_showsAProblem_andTheNextSyncWorks() = runTest {
        fake.edit("alice", rome)
        val sync = manager()
        failApply = true

        assertFalse(sync.sync("alice", full = true))
        settle()
        assertEquals(SyncStatus.PROBLEM, sync.status.value)
        assertEquals(1, fake.rowsOf("alice").count { it.pending })

        failApply = false
        assertTrue(sync.sync("alice", full = true))
        settle()
        assertEquals(SyncStatus.SAVED, sync.status.value)
        assertEquals(0, fake.rowsOf("alice").count { it.pending })
    }

    @Test
    fun unexpectedError_inTheBackground_keepsTheSessionSyncing() = runTest {
        cloud.put("alice", rome)
        failApply = true
        val sync = manager()
        sync.start()
        settle()
        assertEquals(SyncStatus.PROBLEM, sync.status.value)

        // The collectors survived: a local change still starts a quick sync, and a refresh works again.
        failApply = false
        assertTrue(sync.refresh())
        settle()
        assertEquals(SyncStatus.SAVED, sync.status.value)
        assertEquals(setOf("t1"), fake.liveOf("alice").keys)
    }
}
