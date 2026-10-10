package com.example.planyourtrip.ui.common.account

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeSyncController
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.sync.SyncStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val etay = AuthUser(uid = "u1", displayName = "Etay Zahavi", email = "etay@example.com")
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun AccountViewModel.current() = state.filterNotNull().first()

    @Test
    fun showsNameEmailAndInitials() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val state = AccountViewModel(auth, FakeSyncController(auth)).current()

        assertEquals(AccountState("Etay Zahavi", "etay@example.com", "EZ"), state)
    }

    @Test
    fun noDisplayName_usesEmailPrefix() = runTest(dispatcher) {
        val auth = FakeAuthRepository(AuthUser(uid = "u2", displayName = null, email = "dana.levi@example.com"))
        val state = AccountViewModel(auth, FakeSyncController(auth)).current()

        assertEquals("dana.levi", state.userName)
        assertEquals("D", state.initials)
    }

    @Test
    fun syncStatus_isShown() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val sync = FakeSyncController(auth)
        val vm = AccountViewModel(auth, sync)
        assertEquals(SyncStatus.SAVED, vm.current().syncStatus)

        sync.status.value = SyncStatus.OFFLINE

        assertEquals(SyncStatus.OFFLINE, vm.current().syncStatus)
    }

    @Test
    fun signOut_goesThroughTheSync() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val sync = FakeSyncController(auth)
        val vm = AccountViewModel(auth, sync)

        vm.onSignOut()

        assertEquals(listOf(false), sync.signOutCalls)
        assertEquals(listOf("signOut"), auth.calls)
        assertNull(auth.currentUser.value)
    }

    @Test
    fun signOut_withPendingChangesOffline_showsTheWarning_andCancelKeepsTheUserSignedIn() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val sync = FakeSyncController(auth).apply { pendingOffline = true }
        val vm = AccountViewModel(auth, sync)

        vm.onSignOut()
        assertTrue(vm.current().showSignOutWarning)
        assertEquals(etay, auth.currentUser.value)

        vm.onDismissSignOutWarning()
        assertFalse(vm.current().showSignOutWarning)
        assertEquals(etay, auth.currentUser.value)
    }

    @Test
    fun signOutAnyway_forcesTheSignOut() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val sync = FakeSyncController(auth).apply { pendingOffline = true }
        val vm = AccountViewModel(auth, sync)
        vm.onSignOut()

        vm.onConfirmSignOut()

        assertEquals(listOf(false, true), sync.signOutCalls)
        assertNull(auth.currentUser.value)
    }

    @Test
    fun signOut_notConfirmedInTheCloud_showsThatWarning_andSignOutAnywayKeepsTheData() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val sync = FakeSyncController(auth).apply { notConfirmed = true }
        val vm = AccountViewModel(auth, sync)

        vm.onSignOut()
        assertEquals(SignOutWarning.NOT_CONFIRMED, vm.current().signOutWarning)
        assertEquals(etay, auth.currentUser.value)

        vm.onConfirmSignOut()
        assertEquals(listOf(false, true), sync.signOutCalls)
        assertNull(auth.currentUser.value)
    }

    @Test
    fun signOut_withPendingChanges_showsThePendingWarning() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val vm = AccountViewModel(auth, FakeSyncController(auth).apply { pendingOffline = true })

        vm.onSignOut()

        assertEquals(SignOutWarning.PENDING_CHANGES, vm.current().signOutWarning)
    }

    @Test
    fun syncProblem_isShown_withTheTimeOfTheLastSuccessfulSync() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val sync = FakeSyncController(auth)
        val vm = AccountViewModel(auth, sync)

        sync.status.value = SyncStatus.PROBLEM
        sync.lastSyncedAt.value = 1_791_000_000_000

        assertEquals(SyncStatus.PROBLEM, vm.current().syncStatus)
        assertEquals(1_791_000_000_000, vm.current().lastSyncedAt)
    }

    @Test
    fun tapToRetry_runsOneFullSync_evenWhenTappedTwice() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val sync = FakeSyncController(auth).apply { refreshGate = CompletableDeferred() }
        val vm = AccountViewModel(auth, sync)
        sync.status.value = SyncStatus.PROBLEM

        vm.onRetrySync()
        vm.onRetrySync()
        assertTrue(vm.current().isRetrying)

        sync.refreshGate!!.complete(Unit)
        assertEquals(1, sync.refreshCalls)
        assertFalse(vm.current().isRetrying)
    }
}
