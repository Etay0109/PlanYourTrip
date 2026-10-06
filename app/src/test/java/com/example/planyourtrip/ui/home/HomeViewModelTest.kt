package com.example.planyourtrip.ui.home

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeSyncController
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.sync.SyncStatus
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.TripStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val today = LocalDate.of(2026, 10, 5)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val etay = AuthUser(uid = "u1", displayName = "Etay Zahavi", email = "etay@example.com")

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun HomeViewModel.content() = uiState.filterIsInstance<HomeUiState.Content>().first()

    @Test
    fun emptyRepository_givesEmptyContentWithUserInfo() = runTest(dispatcher) {
        val vm = homeViewModel(FakeTripRepository(), FakeAuthRepository(etay))

        val content = vm.content()

        assertEquals("Etay Zahavi", content.userName)
        assertEquals("etay@example.com", content.email)
        assertEquals("Etay", content.firstName)
        assertEquals("EZ", content.initials)
        assertTrue(content.sections.isEmpty)
    }

    @Test
    fun noDisplayName_usesEmailPrefix() = runTest(dispatcher) {
        val user = AuthUser(uid = "u2", displayName = null, email = "dana.levi@example.com")
        val content = homeViewModel(FakeTripRepository(), FakeAuthRepository(user)).content()

        assertEquals("dana.levi", content.userName)
        assertEquals("dana.levi", content.firstName)
        assertEquals("D", content.initials)
    }

    @Test
    fun trips_areGroupedRelativeToClock_andOnlyTheUsersOwn() = runTest(dispatcher) {
        val rome = Trip(1, "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY)
        val prague = Trip(2, "Prague", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), TripColor.MINT)
        val someoneElses = Trip(3, "Oslo", LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 22), TripColor.ROSE)
        val trips = FakeTripRepository(listOf("u1" to prague, "u1" to rome, "other" to someoneElses))
        val vm = homeViewModel(trips, FakeAuthRepository(etay))

        val sections = vm.content().sections

        assertEquals(ActiveTrip(rome, TripStatus.InDays(38)), sections.next)
        assertTrue(sections.upcoming.isEmpty())
        assertEquals(listOf(prague), sections.past)
    }

    @Test
    fun switchingAccount_showsTheNewAccountsTrips() = runTest(dispatcher) {
        val rome = Trip(1, "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY)
        val auth = FakeAuthRepository(etay)
        val vm = homeViewModel(FakeTripRepository(listOf("u1" to rome)), auth)
        assertEquals(rome, vm.content().sections.next?.trip)

        auth.currentUser.value = AuthUser(uid = "u2", displayName = "Dana", email = null)

        val content = vm.uiState.filterIsInstance<HomeUiState.Content>().first { it.userName == "Dana" }
        assertTrue(content.sections.isEmpty)
        assertNull(content.sections.next)
    }

    @Test
    fun signOut_goesThroughTheSync() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val sync = FakeSyncController(auth)
        val vm = homeViewModel(FakeTripRepository(), auth, sync)

        vm.onSignOut()

        assertEquals(listOf(false), sync.signOutCalls)
        assertEquals(listOf("signOut"), auth.calls)
        assertNull(auth.currentUser.value)
    }

    @Test
    fun syncStatus_isShownInTheState() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val sync = FakeSyncController(auth)
        val vm = homeViewModel(FakeTripRepository(), auth, sync)
        assertEquals(SyncStatus.SAVED, vm.content().syncStatus)

        sync.status.value = SyncStatus.OFFLINE

        assertEquals(SyncStatus.OFFLINE, vm.content().syncStatus)
    }

    @Test
    fun signOut_withPendingChangesOffline_showsTheWarning_andCancelKeepsTheUserSignedIn() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val sync = FakeSyncController(auth).apply { pendingOffline = true }
        val vm = homeViewModel(FakeTripRepository(), auth, sync)

        vm.onSignOut()
        assertTrue(vm.content().showSignOutWarning)
        assertEquals(etay, auth.currentUser.value)

        vm.onDismissSignOutWarning()
        assertFalse(vm.content().showSignOutWarning)
        assertEquals(etay, auth.currentUser.value)
    }

    @Test
    fun signOutAnyway_forcesTheSignOut() = runTest(dispatcher) {
        val auth = FakeAuthRepository(etay)
        val sync = FakeSyncController(auth).apply { pendingOffline = true }
        val vm = homeViewModel(FakeTripRepository(), auth, sync)
        vm.onSignOut()

        vm.onConfirmSignOut()

        assertEquals(listOf(false, true), sync.signOutCalls)
        assertNull(auth.currentUser.value)
    }

    private fun homeViewModel(
        trips: FakeTripRepository,
        auth: FakeAuthRepository,
        sync: FakeSyncController = FakeSyncController(auth),
    ) = HomeViewModel(trips, auth, sync, clock, dispatcher)
}
