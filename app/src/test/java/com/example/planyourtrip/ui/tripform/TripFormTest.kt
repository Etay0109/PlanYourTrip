package com.example.planyourtrip.ui.tripform

import androidx.lifecycle.SavedStateHandle
import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeReminderPrompt
import com.example.planyourtrip.FakeCurrencyRepository
import com.example.planyourtrip.FakePlaceSearch
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.place.DestinationSuggestions
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TripFormTest {

    private val dispatcher = StandardTestDispatcher()
    private val user = FakeAuthRepository(AuthUser("u1", "Yael", "yael@example.com"))
    private lateinit var repo: FakeTripRepository
    private val currencies = FakeCurrencyRepository(now = { Instant.EPOCH })
    private val destinations = DestinationSuggestions(FakePlaceSearch(), table = emptyList())
    private lateinit var vm: TripFormViewModel

    private val nov12 = LocalDate.of(2026, 11, 12)
    private val nov17 = LocalDate.of(2026, 11, 17)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeTripRepository()
        vm = TripFormViewModel(tripId = null, repo, user, currencies, destinations)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun validator_rules() {
        assertEquals(TripFieldError.EMPTY_DESTINATION, TripValidator.validateDestination("  "))
        assertNull(TripValidator.validateDestination("Rome"))
        assertEquals(TripFieldError.MISSING_START, TripValidator.validateStart(null))
        assertEquals(TripFieldError.MISSING_END, TripValidator.validateEnd(nov12, null))
        assertEquals(TripFieldError.END_BEFORE_START, TripValidator.validateEnd(nov17, nov12))
        assertNull(TripValidator.validateEnd(nov12, nov12))
        assertNull(TripValidator.validateEnd(nov12, nov17))
    }

    @Test
    fun save_emptyForm_showsAllErrors_andDoesNotSave() = runTest(dispatcher) {
        vm.onSave()
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(TripFieldError.EMPTY_DESTINATION, s.destinationError)
        assertEquals(TripFieldError.MISSING_START, s.startError)
        assertEquals(TripFieldError.MISSING_END, s.endError)
        assertFalse(s.saved)
        assertTrue(repo.trips.value.isEmpty())
    }

    @Test
    fun save_endBeforeStart_showsError() = runTest(dispatcher) {
        vm.onDestinationChange("Rome")
        vm.onStartDateChange(nov17)
        vm.onEndDateChange(nov12)
        vm.onSave()
        advanceUntilIdle()

        assertEquals(TripFieldError.END_BEFORE_START, vm.uiState.value.endError)
        assertTrue(repo.trips.value.isEmpty())
    }

    @Test
    fun changingDate_clearsDateErrors() {
        vm.onStartDateChange(nov17)
        vm.onEndDateChange(nov12)
        vm.onSave()
        vm.onStartDateChange(nov12)
        assertNull(vm.uiState.value.endError)
    }

    @Test
    fun save_valid_addsTripAndMarksSaved() = runTest(dispatcher) {
        assertFalse(vm.uiState.value.isEditing)
        vm.onDestinationChange("  Rome, Italy ")
        vm.onStartDateChange(nov12)
        vm.onEndDateChange(nov17)
        vm.onSave()
        vm.onSave() // a double tap before the save finishes must not create a duplicate
        advanceUntilIdle()

        val trip = repo.tripsOf("u1").single() // saved for the signed-in user
        assertEquals("Rome, Italy", trip.destination)
        assertEquals(nov12, trip.startDate)
        assertEquals(nov17, trip.endDate)
        assertNull(trip.budgetAgorot) // a new trip has no budget yet
        assertTrue(vm.uiState.value.saved)

        vm.onSave() // a second tap after saving must not create a duplicate
        advanceUntilIdle()
        assertEquals(1, repo.trips.value.size)
    }

    @Test
    fun save_withoutSignedInUser_doesNothing() = runTest(dispatcher) {
        val signedOut = TripFormViewModel(tripId = null, repo, FakeAuthRepository(initialUser = null), currencies, destinations)
        signedOut.onDestinationChange("Rome")
        signedOut.onStartDateChange(nov12)
        signedOut.onEndDateChange(nov17)
        signedOut.onSave()
        advanceUntilIdle()

        assertTrue(repo.trips.value.isEmpty())
        assertFalse(signedOut.uiState.value.saved)
    }

    @Test
    fun edit_loadsTrip_andUpdatesItKeepingColorAndBudget() = runTest(dispatcher) {
        val rome = Trip(7, "Rome", nov12, nov17, TripColor.MINT, budgetAgorot = 600_000)
        repo.trips.value = listOf("u1" to rome)
        val edit = TripFormViewModel(tripId = 7, repo, user, currencies, destinations)
        assertTrue(edit.uiState.value.isLoading)
        advanceUntilIdle()

        val loaded = edit.uiState.value
        assertTrue(loaded.isEditing)
        assertFalse(loaded.isLoading)
        assertEquals("Rome", loaded.destination)
        assertEquals(nov12, loaded.startDate)
        assertEquals(nov17, loaded.endDate)

        edit.onDestinationChange(" Rome, Italy ")
        edit.onEndDateChange(nov17.plusDays(2))
        edit.onSave()
        advanceUntilIdle()

        assertEquals(rome.copy(destination = "Rome, Italy", endDate = nov17.plusDays(2)), repo.tripsOf("u1").single())
        assertTrue(edit.uiState.value.saved)
    }

    @Test
    fun edit_someoneElsesTrip_loadsNothing_andChangesNothing() = runTest(dispatcher) {
        val oslo = Trip(3, "Oslo", nov12, nov17, TripColor.ROSE)
        repo.trips.value = listOf("other" to oslo)
        val edit = TripFormViewModel(tripId = 3, repo, user, currencies, destinations)
        advanceUntilIdle()
        assertEquals("", edit.uiState.value.destination)

        edit.onDestinationChange("Hacked")
        edit.onStartDateChange(nov12)
        edit.onEndDateChange(nov17)
        edit.onSave()
        advanceUntilIdle()
        assertEquals(oslo, repo.tripsOf("other").single())
    }

    // ---------- Trip currency ----------

    private fun fillValidForm(vm: TripFormViewModel) {
        vm.onDestinationChange("Rome")
        vm.onStartDateChange(nov12)
        vm.onEndDateChange(nov17)
    }

    @Test
    fun newTrip_hasNoCurrency_andSavesWithout() = runTest(dispatcher) {
        assertNull(vm.uiState.value.currencyCode) // nothing preselected, not even from "Rome"
        fillValidForm(vm)
        vm.onSave()
        advanceUntilIdle()
        assertNull(repo.tripsOf("u1").single().currencyCode)
    }

    @Test
    fun newTrip_pickedCurrency_isSaved() = runTest(dispatcher) {
        fillValidForm(vm)
        vm.onChooseCurrency()
        advanceUntilIdle()
        val picker = vm.uiState.value.currencyPicker!!
        assertNull(picker.selectedCode)
        assertEquals(listOf("EUR", "USD"), picker.results.map { it.code })

        vm.onCurrencyQueryChange("euro")
        assertEquals(listOf("EUR"), vm.uiState.value.currencyPicker?.results?.map { it.code })
        vm.onCurrencySelected("EUR")
        assertNull(vm.uiState.value.currencyPicker)
        assertEquals("EUR", vm.uiState.value.currencyCode)
        assertEquals("Euro", vm.uiState.value.currencyName)

        vm.onSave()
        advanceUntilIdle()
        assertEquals("EUR", repo.tripsOf("u1").single().currencyCode)
    }

    @Test
    fun edit_prefillsCurrency_andSavesTheChange_orItsRemoval() = runTest(dispatcher) {
        repo.trips.value = listOf("u1" to Trip(7, "Rome", nov12, nov17, TripColor.MINT, currencyCode = "EUR"))
        val edit = TripFormViewModel(tripId = 7, repo, user, currencies, destinations)
        advanceUntilIdle()
        assertEquals("EUR", edit.uiState.value.currencyCode)

        edit.onChooseCurrency()
        advanceUntilIdle()
        assertEquals("EUR", edit.uiState.value.currencyPicker?.selectedCode)
        edit.onCurrencySelected("USD")
        assertEquals("US Dollar", edit.uiState.value.currencyName) // name from the service's list
        edit.onSave()
        advanceUntilIdle()
        assertEquals("USD", repo.tripsOf("u1").single().currencyCode)

        val again = TripFormViewModel(tripId = 7, repo, user, currencies, destinations)
        advanceUntilIdle()
        again.onClearCurrency()
        assertNull(again.uiState.value.currencyCode)
        again.onSave()
        advanceUntilIdle()
        assertNull(repo.tripsOf("u1").single().currencyCode)
    }

    @Test
    fun currencyList_offline_showsRetry_andDismissKeepsTheChoice() = runTest(dispatcher) {
        currencies.offline = true
        vm.onChooseCurrency()
        advanceUntilIdle()
        assertEquals(true, vm.uiState.value.currencyPicker?.failed)

        currencies.offline = false
        vm.onRetryCurrencies()
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.currencyPicker?.results?.size)

        vm.onDismissCurrencyPicker()
        assertNull(vm.uiState.value.currencyPicker)
        assertNull(vm.uiState.value.currencyCode)
    }

    @Test
    fun aNewTrip_tellsTheReminderPrompt_anEditDoesNot() = runTest(dispatcher) {
        val prompt = FakeReminderPrompt()
        val create = TripFormViewModel(tripId = null, repo, user, currencies, destinations, prompt)
        create.onDestinationChange("Rome")
        create.onStartDateChange(nov12)
        create.onEndDateChange(nov17)
        create.onSave()
        advanceUntilIdle()
        assertEquals(listOf(nov12), prompt.added)

        val edit = TripFormViewModel(tripId = repo.tripsOf("u1").single().id, repo, user, currencies, destinations, prompt)
        advanceUntilIdle()
        edit.onEndDateChange(nov17.plusDays(1))
        edit.onSave()
        advanceUntilIdle()
        assertEquals(listOf(nov12), prompt.added)
    }

    // ---------- Process death ----------

    @Test
    fun newTrip_draftComesBackAfterProcessDeath() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val before = TripFormViewModel(tripId = null, repo, user, currencies, destinations, savedState = saved)
        before.onDestinationChange("Lisbon")
        before.onStartDateChange(nov12)
        before.onEndDateChange(nov17)
        advanceUntilIdle()

        val after = TripFormViewModel(tripId = null, repo, user, currencies, destinations, savedState = saved).uiState.value
        assertEquals("Lisbon", after.destination)
        assertEquals(nov12, after.startDate)
        assertEquals(nov17, after.endDate)
    }

    @Test
    fun edit_restoredDraftWinsOverTheStoredTrip() = runTest(dispatcher) {
        repo.trips.value = listOf("u1" to Trip(7, "Rome", nov12, nov17, TripColor.MINT))
        val saved = SavedStateHandle()
        val before = TripFormViewModel(tripId = 7, repo, user, currencies, destinations, savedState = saved)
        advanceUntilIdle()
        before.onDestinationChange("Roma")

        val after = TripFormViewModel(tripId = 7, repo, user, currencies, destinations, savedState = saved)
        advanceUntilIdle()
        assertFalse(after.uiState.value.isLoading)
        assertEquals("Roma", after.uiState.value.destination)
        assertEquals(nov12, after.uiState.value.startDate)
    }
}
