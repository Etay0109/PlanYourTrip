package com.example.planyourtrip.ui.tripform

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeCurrencyRepository
import com.example.planyourtrip.FakePlaceSearch
import com.example.planyourtrip.FakePlaceSearch.Companion.CAIRO
import com.example.planyourtrip.FakePlaceSearch.Companion.LONDON
import com.example.planyourtrip.FakePlaceSearch.Companion.LONDON_ONTARIO
import com.example.planyourtrip.FakePlaceSearch.Companion.ROME
import com.example.planyourtrip.FakePlaceSearch.Companion.TOKYO
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.currency.Currency
import com.example.planyourtrip.data.place.DestinationSuggestions
import com.example.planyourtrip.data.place.HebrewPlace
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** Destination suggestions in the trip form: picking, free text, editing after picking, failures, the currency. */
@OptIn(ExperimentalCoroutinesApi::class)
class TripFormPlaceTest {

    private val dispatcher = StandardTestDispatcher()
    private val user = FakeAuthRepository(AuthUser("u1", "Test User", "user@example.com"))
    private lateinit var repo: FakeTripRepository
    private val currencies = FakeCurrencyRepository(now = { Instant.EPOCH }).apply {
        currencyList = listOf(
            Currency("EUR", "Euro"), Currency("GBP", "British Pound"), Currency("JPY", "Japanese Yen"),
            Currency("USD", "US Dollar"),
        )
    }
    private val search = FakePlaceSearch(
        mapOf(
            "lond" to listOf(LONDON, LONDON_ONTARIO), "London" to listOf(LONDON), "rome" to listOf(ROME),
            "toky" to listOf(TOKYO), "cairo" to listOf(CAIRO),
        ),
    )
    private val hebrew = listOf(HebrewPlace("לונדון", emptyList(), LONDON))
    private lateinit var vm: TripFormViewModel

    private val nov12 = LocalDate.of(2026, 11, 12)
    private val nov17 = LocalDate.of(2026, 11, 17)

    private fun form(tripId: Long? = null) =
        TripFormViewModel(tripId, repo, user, currencies, DestinationSuggestions(search, hebrew))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTripRepository()
        vm = form()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Types [text] and waits for the lookup. */
    private fun TestScope.type(text: String, into: TripFormViewModel = vm) {
        into.onDestinationChange(text)
        advanceUntilIdle()
    }

    private fun TestScope.saveWithDates(into: TripFormViewModel = vm) {
        into.onStartDateChange(nov12)
        into.onEndDateChange(nov17)
        into.onSave()
        advanceUntilIdle()
    }

    private fun saved() = repo.tripsOf("u1").single()

    // ---------- Suggestions ----------

    @Test
    fun typing_showsSuggestions_withNameRegionCountryAndFlagCode() = runTest(dispatcher) {
        type("lond")
        val list = vm.uiState.value.suggestions
        assertEquals(listOf("London, United Kingdom", "London, Canada"), list.map { it.label })
        assertEquals(listOf("England, United Kingdom", "Ontario, Canada"), list.map { it.detail })
        assertEquals(listOf("GB", "CA"), list.map { it.countryCode })
    }

    @Test
    fun picking_fillsNameAndCountry_andSavesTheCodeAndCoordinates() = runTest(dispatcher) {
        type("lond")
        vm.onSuggestionPicked(LONDON)
        val state = vm.uiState.value
        assertEquals("London, United Kingdom", state.destination)
        assertTrue(state.suggestions.isEmpty())

        saveWithDates()
        val trip = saved()
        assertEquals("London, United Kingdom", trip.destination)
        assertEquals("GB", trip.countryCode)
        assertEquals(51.5074456, trip.latitude!!, 0.0)
        assertEquals(-0.1277653, trip.longitude!!, 0.0)
    }

    @Test
    fun freeText_isSavedWithoutCountryOrCoordinates() = runTest(dispatcher) {
        type("lond")
        type("London and around") // never picked
        saveWithDates()
        val trip = saved()
        assertEquals("London and around", trip.destination)
        assertNull(trip.countryCode)
        assertNull(trip.latitude)
        assertNull(trip.longitude)
    }

    @Test
    fun editingTheTextAfterPicking_forgetsThePlace_untilASuggestionIsPickedAgain() = runTest(dispatcher) {
        type("lond")
        vm.onSuggestionPicked(LONDON)
        type("London, United Kingdo")
        assertNull(vm.uiState.value.place)
        type("London, United Kingdom") // the same text again, typed: still free text
        assertNull(vm.uiState.value.place)
        saveWithDates()
        assertNull(saved().countryCode)
    }

    @Test
    fun pickingAgainAfterEditing_remembersTheNewPlace() = runTest(dispatcher) {
        type("lond")
        vm.onSuggestionPicked(LONDON)
        type("rome")
        vm.onSuggestionPicked(ROME)
        saveWithDates()
        assertEquals("Rome, Italy", saved().destination)
        assertEquals("IT", saved().countryCode)
    }

    @Test
    fun offline_noListAndNoError_andTheTextIsSaved() = runTest(dispatcher) {
        search.offline = true
        type("lond")
        val state = vm.uiState.value
        assertTrue(state.suggestions.isEmpty())
        assertNull(state.destinationError)
        saveWithDates()
        assertEquals("lond", saved().destination)
        assertNull(saved().countryCode)
    }

    @Test
    fun aFailingService_isLikeOffline() = runTest(dispatcher) {
        val failing = TripFormViewModel(
            null, repo, user, currencies, DestinationSuggestions({ error("HTTP 503") }, emptyList()),
        )
        type("lond", into = failing)
        assertTrue(failing.uiState.value.suggestions.isEmpty())
        assertNull(failing.uiState.value.destinationError)
    }

    @Test
    fun hebrewInput_findsPlacesAbroad_evenOffline() = runTest(dispatcher) {
        search.offline = true
        type("לונד")
        assertEquals(listOf("London, United Kingdom"), vm.uiState.value.suggestions.map { it.label })
        vm.onSuggestionPicked(vm.uiState.value.suggestions.single())
        saveWithDates()
        assertEquals("London, United Kingdom", saved().destination)
        assertEquals("GB", saved().countryCode)
    }

    @Test
    fun lessThanTwoCharacters_isNotLookedUp_andClosesTheList() = runTest(dispatcher) {
        type("lond")
        type("l")
        assertTrue(vm.uiState.value.suggestions.isEmpty())
        type(" l ")
        assertEquals(listOf("lond"), search.queries)
    }

    @Test
    fun fastTyping_looksUpOnlyTheLastText_afterAShortPause() = runTest(dispatcher) {
        vm.onDestinationChange("lo")
        advanceTimeBy(100)
        vm.onDestinationChange("lon")
        advanceTimeBy(100)
        vm.onDestinationChange("lond")
        advanceTimeBy(TripFormViewModel.LOOKUP_DELAY_MILLIS - 1)
        runCurrent()
        assertTrue(search.queries.isEmpty())
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("lond"), search.queries)
    }

    @Test
    fun aNewKeystroke_cancelsTheRunningLookup_andItsAnswerIsNeverShown() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        search.gate = gate
        vm.onDestinationChange("lond")
        advanceTimeBy(TripFormViewModel.LOOKUP_DELAY_MILLIS + 1)
        runCurrent()
        assertEquals(listOf("lond"), search.queries)

        search.gate = null
        type("rome")
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("rome"), search.completed) // "lond" was cancelled
        assertEquals(listOf("Rome, Italy"), vm.uiState.value.suggestions.map { it.label })
    }

    @Test
    fun leavingTheField_closesTheList_andKeepsTheText() = runTest(dispatcher) {
        type("lond")
        vm.onSuggestionsDismissed()
        assertTrue(vm.uiState.value.suggestions.isEmpty())
        assertEquals("lond", vm.uiState.value.destination)
    }

    // ---------- Edit trip ----------

    private val london = Trip(
        7, "London, United Kingdom", nov12, nov17, TripColor.MINT, currencyCode = "EUR",
        countryCode = "GB", latitude = 51.5, longitude = -0.13,
    )

    @Test
    fun edit_keepsTheStoredPlace_whileTheTextIsUnchanged() = runTest(dispatcher) {
        repo.trips.value = listOf("u1" to london)
        val edit = form(tripId = 7)
        advanceUntilIdle()
        edit.onEndDateChange(nov17.plusDays(1))
        edit.onSave()
        advanceUntilIdle()
        assertEquals(london.copy(endDate = nov17.plusDays(1)), saved())
    }

    @Test
    fun edit_editingTheText_clearsTheStoredPlace() = runTest(dispatcher) {
        repo.trips.value = listOf("u1" to london)
        val edit = form(tripId = 7)
        advanceUntilIdle()
        type("London and Oxford", into = edit)
        edit.onSave()
        advanceUntilIdle()
        assertEquals(london.copy(destination = "London and Oxford", countryCode = null, latitude = null, longitude = null), saved())
    }

    @Test
    fun edit_pickingAPlace_neverChangesTheCurrency() = runTest(dispatcher) {
        repo.trips.value = listOf("u1" to london.copy(currencyCode = null))
        val edit = form(tripId = 7)
        advanceUntilIdle()
        type("toky", into = edit)
        edit.onSuggestionPicked(TOKYO)
        advanceUntilIdle()
        assertNull(edit.uiState.value.currencyCode)
        edit.onSave()
        advanceUntilIdle()
        assertEquals("JP", saved().countryCode)
        assertNull(saved().currencyCode)
    }

    // ---------- Currency preselection (new trip) ----------

    @Test
    fun newTrip_pickingAPlace_preselectsItsCountrysCurrency_whichCanBeChanged() = runTest(dispatcher) {
        type("lond")
        vm.onSuggestionPicked(LONDON)
        advanceUntilIdle()
        assertEquals("GBP", vm.uiState.value.currencyCode)
        assertEquals("British Pound", vm.uiState.value.currencyName)

        vm.onCurrencySelected("EUR")
        saveWithDates()
        assertEquals("EUR", saved().currencyCode)
    }

    @Test
    fun aCurrencyChosenBeforePicking_isKept() = runTest(dispatcher) {
        vm.onCurrencySelected("USD")
        type("lond")
        vm.onSuggestionPicked(LONDON)
        advanceUntilIdle()
        assertEquals("USD", vm.uiState.value.currencyCode)
    }

    @Test
    fun aPreselectedCurrency_followsTheNextPick_untilTheUserTouchesIt() = runTest(dispatcher) {
        type("lond")
        vm.onSuggestionPicked(LONDON)
        advanceUntilIdle()
        type("toky")
        vm.onSuggestionPicked(TOKYO)
        advanceUntilIdle()
        assertEquals("JPY", vm.uiState.value.currencyCode)

        vm.onCurrencySelected("USD") // chosen by hand now
        type("rome")
        vm.onSuggestionPicked(ROME)
        advanceUntilIdle()
        assertEquals("USD", vm.uiState.value.currencyCode)
    }

    @Test
    fun clearingAPreselectedCurrency_endsThePreselection() = runTest(dispatcher) {
        type("lond")
        vm.onSuggestionPicked(LONDON)
        advanceUntilIdle()
        vm.onClearCurrency()
        assertNull(vm.uiState.value.currencyCode)
        assertFalse(vm.uiState.value.currencyPreselected)
    }

    @Test
    fun aCountryWhoseCurrencyTheAppDoesNotOffer_preselectsNothing_andDropsAnEarlierPreselection() = runTest(dispatcher) {
        type("cairo")
        vm.onSuggestionPicked(CAIRO) // EGP isn't offered
        advanceUntilIdle()
        assertNull(vm.uiState.value.currencyCode)

        type("lond")
        vm.onSuggestionPicked(LONDON)
        advanceUntilIdle()
        type("cairo")
        vm.onSuggestionPicked(CAIRO)
        advanceUntilIdle()
        assertNull(vm.uiState.value.currencyCode)
    }

    @Test
    fun theCurrencyListUnavailable_preselectsNothing() = runTest(dispatcher) {
        currencies.offline = true
        type("lond")
        vm.onSuggestionPicked(LONDON)
        advanceUntilIdle()
        assertNull(vm.uiState.value.currencyCode)
    }

    @Test
    fun freeText_neverPreselectsACurrency() = runTest(dispatcher) {
        type("London")
        advanceUntilIdle()
        assertNull(vm.uiState.value.currencyCode)
    }
}
