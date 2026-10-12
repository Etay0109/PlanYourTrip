package com.example.planyourtrip.ui.common.refresh

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeSyncController
import com.example.planyourtrip.data.auth.AuthUser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val sync = FakeSyncController(FakeAuthRepository(AuthUser("u1", "Yael", "yael@example.com")))

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun aRefresh_showsTheSpinnerUntilTheSyncEnds() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        sync.refreshGate = gate
        val vm = RefreshViewModel(sync)

        vm.onRefresh()
        assertTrue(vm.state.value.isRefreshing)

        gate.complete(Unit)
        assertEquals(RefreshState(), vm.state.value)
        assertEquals(1, sync.refreshCalls)
    }

    @Test
    fun pullingAgainWhileItRuns_startsNoSecondRefresh() = runTest(dispatcher) {
        sync.refreshGate = CompletableDeferred()
        val vm = RefreshViewModel(sync)

        vm.onRefresh()
        vm.onRefresh()

        assertEquals(1, sync.refreshCalls)
    }

    @Test
    fun aFailedRefresh_showsTheMessageOnce() = runTest(dispatcher) {
        sync.refreshResult = false
        val vm = RefreshViewModel(sync)

        vm.onRefresh()
        assertTrue(vm.state.value.failed)
        assertFalse(vm.state.value.isRefreshing)

        vm.onMessageShown()
        assertFalse(vm.state.value.failed)
    }
}
