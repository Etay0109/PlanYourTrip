package com.example.planyourtrip.ui.home

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
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
        assertEquals("Etay", content.firstName)
        assertTrue(content.sections.isEmpty)
    }

    @Test
    fun noDisplayName_usesEmailPrefix() = runTest(dispatcher) {
        val user = AuthUser(uid = "u2", displayName = null, email = "dana.levi@example.com")
        val content = homeViewModel(FakeTripRepository(), FakeAuthRepository(user)).content()

        assertEquals("dana.levi", content.userName)
        assertEquals("dana.levi", content.firstName)
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
    fun deletingTheNextTrip_theNextUpcomingTripTakesItsPlace() = runTest(dispatcher) {
        val rome = Trip(1, "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY)
        val athens = Trip(2, "Athens", LocalDate.of(2027, 2, 3), LocalDate.of(2027, 2, 7), TripColor.PEACH)
        val lisbon = Trip(3, "Lisbon", LocalDate.of(2027, 4, 20), LocalDate.of(2027, 4, 26), TripColor.MINT)
        val trips = FakeTripRepository(listOf("u1" to rome, "u1" to athens, "u1" to lisbon))
        val vm = homeViewModel(trips, FakeAuthRepository(etay))
        assertEquals(rome, vm.content().sections.next?.trip)

        trips.deleteTrip(1, "u1")

        val sections = vm.uiState.filterIsInstance<HomeUiState.Content>()
            .first { it.sections.next?.trip != rome }.sections
        assertEquals(athens, sections.next?.trip)
        assertEquals(listOf(lisbon), sections.upcoming.map { it.trip })
    }

    @Test
    fun deletingTheLastTrip_showsTheEmptyState() = runTest(dispatcher) {
        val rome = Trip(1, "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY)
        val trips = FakeTripRepository(listOf("u1" to rome))
        val vm = homeViewModel(trips, FakeAuthRepository(etay))
        assertEquals(rome, vm.content().sections.next?.trip)

        trips.deleteTrip(1, "u1")

        val content = vm.uiState.filterIsInstance<HomeUiState.Content>().first { it.sections.isEmpty }
        assertNull(content.sections.next)
        assertTrue(content.sections.past.isEmpty())
    }

    private fun homeViewModel(trips: FakeTripRepository, auth: FakeAuthRepository) =
        HomeViewModel(trips, auth, clock, dispatcher)
}
