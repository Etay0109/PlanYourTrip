package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeCurrencyRepository
import com.example.planyourtrip.FakeExpenseRepository
import com.example.planyourtrip.FakeTravelerRepository
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.ME
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.currency.ExchangeRate
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ForeignAmount
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.tripdetails.expense.ConversionPreview
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseFieldError
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormViewModel
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseValidator
import com.example.planyourtrip.ui.tripdetails.expense.ForeignRateState
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseFormTest {

    private val today = LocalDate.of(2026, 10, 5)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()
    private val auth = FakeAuthRepository(AuthUser("u1", "Yael", "yael@example.com"))

    private val rome = Trip(1, "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY)
    private val dinner = Expense(5, 1, "Dinner", 12_050, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13), paidBy = ME, splitBetween = listOf(ME))
    private val rateDate = LocalDate.of(2026, 10, 2)
    private lateinit var trips: FakeTripRepository
    private lateinit var repo: FakeExpenseRepository
    private lateinit var currencies: FakeCurrencyRepository
    private lateinit var travelers: FakeTravelerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trips = FakeTripRepository(listOf("u1" to rome))
        repo = FakeExpenseRepository(trips, listOf(dinner))
        currencies = FakeCurrencyRepository(now = clock::instant)
        travelers = FakeTravelerRepository(trips, repo)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(expenseId: Long? = null, user: FakeAuthRepository = auth) =
        ExpenseFormViewModel(1, expenseId, repo, trips, currencies, user, clock, travelers)

    private fun tripInEuro() {
        trips.trips.value = listOf("u1" to rome.copy(currencyCode = "EUR"))
    }

    private fun cachedEuro(rate: String, age: Duration = Duration.ofHours(1)) {
        currencies.cache.value += "EUR" to ExchangeRate("EUR", BigDecimal(rate), rateDate, clock.instant() - age)
    }

    @Test
    fun nameValidator() {
        assertEquals(ExpenseFieldError.EMPTY_NAME, ExpenseValidator.validateName(" "))
        assertNull(ExpenseValidator.validateName("Taxi"))
    }

    @Test
    fun newExpense_defaultsToTodayOtherAndShekels_whenTheTripHasNoCurrency() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertFalse(s.isEditing)
        assertFalse(s.isLoading)
        assertEquals(today, s.date)
        assertEquals(ExpenseCategory.OTHER, s.category)
        assertEquals(ILS, s.currencyCode)
        assertNull(s.foreignCode) // only ILS
        assertTrue(currencies.refreshCalls.isEmpty())
    }

    @Test
    fun shekelTrip_offersOnlyShekels() = runTest(dispatcher) {
        trips.trips.value = listOf("u1" to rome.copy(currencyCode = ILS))
        val vm = vm()
        advanceUntilIdle()
        assertNull(vm.uiState.value.foreignCode)
        assertEquals(ILS, vm.uiState.value.currencyCode)
    }

    @Test
    fun save_invalid_showsErrors_andEditingClearsOnlyThatError() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onAmountChange("0")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(ExpenseFieldError.EMPTY_NAME, vm.uiState.value.nameError)
        assertEquals(ExpenseFieldError.INVALID_AMOUNT, vm.uiState.value.amountError)
        assertEquals(listOf(dinner), repo.expenses.value)

        vm.onNameChange("Taxi")
        assertNull(vm.uiState.value.nameError)
        assertEquals(ExpenseFieldError.INVALID_AMOUNT, vm.uiState.value.amountError)
    }

    @Test
    fun save_valid_addsExpenseInAgorot() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onNameChange("  Taxi to hotel ")
        vm.onAmountChange("85.5")
        vm.onCategoryChange(ExpenseCategory.TRANSPORT)
        vm.onDateChange(LocalDate.of(2026, 11, 12))
        vm.onSave()
        vm.onSave() // double tap
        advanceUntilIdle()

        val added = repo.expenses.value.single { it.id != dinner.id }
        assertEquals(Expense(added.id, 1, "Taxi to hotel", 8_550, ExpenseCategory.TRANSPORT, LocalDate.of(2026, 11, 12), paidBy = ME, splitBetween = listOf(ME)), added)
        assertEquals(2, repo.expenses.value.size)
        assertTrue(vm.uiState.value.done)
    }

    // ---------- Date limit: never after the trip's last day ----------

    @Test
    fun dateValidator_rejectsOnlyDatesAfterTheTrip() {
        val end = rome.endDate
        assertNull(ExpenseValidator.validateDate(end, end))
        assertNull(ExpenseValidator.validateDate(LocalDate.of(2026, 1, 1), end)) // before the trip is fine
        assertEquals(ExpenseFieldError.DATE_AFTER_TRIP, ExpenseValidator.validateDate(end.plusDays(1), end))
        assertNull(ExpenseValidator.validateDate(end.plusDays(1), null))
    }

    @Test
    fun defaultDate_isTodayUnlessTheTripHasEnded() {
        val end = rome.endDate
        assertEquals(today, ExpenseValidator.defaultDate(today, end))
        assertEquals(end, ExpenseValidator.defaultDate(end, end))
        assertEquals(end, ExpenseValidator.defaultDate(end.plusDays(3), end))
        assertEquals(today, ExpenseValidator.defaultDate(today, null))
    }

    @Test
    fun newExpense_exposesTheTripEndAsTheLatestDate() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        assertEquals(rome.endDate, vm.uiState.value.tripEnd)
        assertNull(vm.uiState.value.dateError)
    }

    @Test
    fun newExpense_afterTheTripEnded_defaultsToItsLastDay() = runTest(dispatcher) {
        val past = rome.copy(startDate = LocalDate.of(2026, 9, 1), endDate = LocalDate.of(2026, 9, 4))
        trips.trips.value = listOf("u1" to past)
        val vm = vm()
        advanceUntilIdle()
        assertEquals(past.endDate, vm.uiState.value.date)
        assertNull(vm.uiState.value.dateError)
    }

    @Test
    fun newExpense_beforeTheTrip_defaultsToToday_andCanBeSaved() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onNameChange("Flight")
        vm.onAmountChange("900")
        vm.onSave()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.done)
        assertEquals(today, repo.expenses.value.single { it.name == "Flight" }.date)
    }

    @Test
    fun save_withADateAfterTheTrip_showsTheDateError_andDoesNotSave() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onNameChange("Souvenir")
        vm.onAmountChange("30")
        vm.onDateChange(rome.endDate.plusDays(1))
        vm.onSave()
        advanceUntilIdle()
        assertEquals(ExpenseFieldError.DATE_AFTER_TRIP, vm.uiState.value.dateError)
        assertNull(vm.uiState.value.nameError)
        assertNull(vm.uiState.value.amountError)
        assertFalse(vm.uiState.value.done)
        assertEquals(listOf(dinner), repo.expenses.value)

        vm.onDateChange(rome.endDate)
        assertNull(vm.uiState.value.dateError)
        vm.onSave()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.done)
        assertEquals(rome.endDate, repo.expenses.value.single { it.name == "Souvenir" }.date)
    }

    @Test
    fun editingAnExpenseDatedAfterTheTrip_keepsItsDate_andShowsTheErrorUntilFixed() = runTest(dispatcher) {
        val late = dinner.copy(date = LocalDate.of(2026, 11, 20)) // the trip was shortened after it was added
        repo.expenses.value = listOf(late)
        val vm = vm(expenseId = late.id)
        advanceUntilIdle()
        assertEquals(late.date, vm.uiState.value.date)
        assertEquals(ExpenseFieldError.DATE_AFTER_TRIP, vm.uiState.value.dateError)

        vm.onSave()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.done)
        assertEquals(listOf(late), repo.expenses.value) // untouched

        vm.onDateChange(LocalDate.of(2026, 11, 16))
        assertNull(vm.uiState.value.dateError)
        vm.onSave()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.done)
        assertEquals(LocalDate.of(2026, 11, 16), repo.expenses.value.single().date)
    }

    // ---------- Trip currency ----------

    @Test
    fun tripCurrency_isTheDefault_andShowsTheLiveConversion() = runTest(dispatcher) {
        tripInEuro()
        cachedEuro("4.02")
        val vm = vm()
        advanceUntilIdle()
        assertEquals("EUR", vm.uiState.value.currencyCode)
        assertEquals("EUR", vm.uiState.value.foreignCode)
        assertTrue(currencies.refreshCalls.isEmpty()) // fresh cache

        vm.onAmountChange("10")
        assertEquals(ConversionPreview(4_020, "EUR", BigDecimal("4.02"), rateDate), vm.uiState.value.preview)
        vm.onAmountChange("10.123")
        assertNull(vm.uiState.value.preview)
    }

    @Test
    fun staleRate_isRefreshedWhenTheFormOpens() = runTest(dispatcher) {
        tripInEuro()
        cachedEuro("4.02", age = Duration.ofHours(13))
        currencies.nextRates["EUR"] = BigDecimal("4.10") to LocalDate.of(2026, 10, 5)
        val vm = vm()
        advanceUntilIdle()
        assertEquals(listOf("EUR"), currencies.refreshCalls)
        vm.onAmountChange("10")
        assertEquals(4_100L, vm.uiState.value.preview?.agorot)
    }

    @Test
    fun save_inTheTripCurrency_storesShekelsAndTheOriginal() = runTest(dispatcher) {
        tripInEuro()
        cachedEuro("4.02")
        val vm = vm()
        advanceUntilIdle()
        vm.onNameChange("Gelato")
        vm.onAmountChange("10")
        vm.onSave()
        advanceUntilIdle()

        val added = repo.expenses.value.single { it.id != dinner.id }
        assertEquals(4_020L, added.amountAgorot)
        assertEquals(ForeignAmount(BigDecimal("10.00"), "EUR", BigDecimal("4.02"), rateDate), added.original)
    }

    @Test
    fun noRateAtAll_disablesTheTripCurrency_andFallsBackToShekels() = runTest(dispatcher) {
        tripInEuro()
        currencies.offline = true
        val vm = vm()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(ForeignRateState.Unavailable, s.foreignRate)
        assertFalse(s.foreignEnabled)
        assertEquals(ILS, s.currencyCode)

        vm.onCurrencyChange("EUR") // disabled
        assertEquals(ILS, vm.uiState.value.currencyCode)

        vm.onNameChange("Taxi")
        vm.onAmountChange("50")
        vm.onSave()
        advanceUntilIdle()
        val added = repo.expenses.value.single { it.id != dinner.id }
        assertEquals(5_000L, added.amountAgorot)
        assertNull(added.original)
    }

    @Test
    fun offlineWithACachedRate_stillUsesIt() = runTest(dispatcher) {
        tripInEuro()
        cachedEuro("4.02", age = Duration.ofDays(3))
        currencies.offline = true
        val vm = vm()
        advanceUntilIdle()
        assertEquals("EUR", vm.uiState.value.currencyCode)
        vm.onAmountChange("10")
        assertEquals(4_020L, vm.uiState.value.preview?.agorot)
    }

    @Test
    fun switchingTheCurrency_keepsTheTextAndChangesItsMeaning() = runTest(dispatcher) {
        tripInEuro()
        cachedEuro("4.02")
        val vm = vm()
        advanceUntilIdle()
        vm.onNameChange("Coffee")
        vm.onAmountChange("10")
        vm.onCurrencyChange(ILS)
        assertEquals("10", vm.uiState.value.amount)
        assertNull(vm.uiState.value.preview)
        vm.onSave()
        advanceUntilIdle()
        val added = repo.expenses.value.single { it.id != dinner.id }
        assertEquals(1_000L, added.amountAgorot)
        assertNull(added.original)
    }

    // ---------- Editing ----------

    private val gelato = Expense(
        7, 1, "Gelato", 4_020, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13),
        ForeignAmount(BigDecimal("10.00"), "EUR", BigDecimal("4.02"), rateDate),
        paidBy = ME, splitBetween = listOf(ME),
    )

    @Test
    fun edit_prefillsAndUpdates() = runTest(dispatcher) {
        val vm = vm(expenseId = 5)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s.isEditing)
        assertEquals("Dinner", s.name)
        assertEquals("120.50", s.amount)
        assertEquals(ILS, s.currencyCode)
        assertEquals(ExpenseCategory.FOOD, s.category)
        assertEquals(dinner.date, s.date)

        vm.onAmountChange("130")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf(dinner.copy(amountAgorot = 13_000)), repo.expenses.value)
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun editingAForeignExpense_withoutChangingTheAmount_keepsItsRate() = runTest(dispatcher) {
        tripInEuro()
        repo.expenses.value = listOf(gelato)
        cachedEuro("4.50") // the rate went up since
        val vm = vm(expenseId = 7)
        advanceUntilIdle()
        assertEquals("10.00", vm.uiState.value.amount)
        assertEquals("EUR", vm.uiState.value.currencyCode)
        assertEquals(4_020L, vm.uiState.value.preview?.agorot)

        vm.onNameChange("Two gelati")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(gelato.copy(name = "Two gelati"), repo.expenses.value.single())
    }

    @Test
    fun editingTheAmount_usesTheLatestRate() = runTest(dispatcher) {
        tripInEuro()
        repo.expenses.value = listOf(gelato)
        cachedEuro("4.50")
        val vm = vm(expenseId = 7)
        advanceUntilIdle()
        vm.onAmountChange("12")
        vm.onSave()
        advanceUntilIdle()
        val updated = repo.expenses.value.single()
        assertEquals(5_400L, updated.amountAgorot)
        assertEquals(ForeignAmount(BigDecimal("12.00"), "EUR", BigDecimal("4.50"), rateDate), updated.original)
    }

    @Test
    fun editingAForeignExpense_afterTheTripCurrencyChanged_keepsItsCurrency() = runTest(dispatcher) {
        trips.trips.value = listOf("u1" to rome.copy(currencyCode = "USD"))
        repo.expenses.value = listOf(gelato)
        val vm = vm(expenseId = 7)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals("EUR", s.foreignCode)
        assertEquals("EUR", s.currencyCode) // not switched to ILS, although there is no EUR rate
        assertTrue(s.foreignEnabled)
        vm.onSave()
        advanceUntilIdle()
        assertEquals(gelato, repo.expenses.value.single()) // kept with its stored rate

        // Changing the amount needs a rate, which isn't there.
        val again = vm(expenseId = 7)
        advanceUntilIdle()
        again.onAmountChange("12")
        again.onSave()
        advanceUntilIdle()
        assertEquals(ExpenseFieldError.NO_RATE, again.uiState.value.amountError)
        assertEquals(gelato, repo.expenses.value.single())
    }

    @Test
    fun delete_removesExpense() = runTest(dispatcher) {
        val vm = vm(expenseId = 5)
        advanceUntilIdle()
        vm.onDelete()
        advanceUntilIdle()
        assertTrue(repo.expenses.value.isEmpty())
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun otherAccount_cannotEditOrDelete() = runTest(dispatcher) {
        val stranger = vm(expenseId = 5, user = FakeAuthRepository(AuthUser("u2", null, "x@y.z")))
        advanceUntilIdle()
        assertEquals("", stranger.uiState.value.name)
        stranger.onDelete()
        advanceUntilIdle()
        assertEquals(listOf(dinner), repo.expenses.value)
    }

    // ---- Paid by / Split between ----

    private val noa = Traveler(1, 1, "noa", "Noa", TravelerColor.ROSE)
    private val amit = Traveler(2, 1, "amit", "Amit", TravelerColor.INDIGO)

    private fun withTravelers() {
        travelers.travelers.value = listOf(noa, amit)
    }

    @Test
    fun onlyMe_hidesTheSplit_andSavesPaidByMeNotShared() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.showSplit)
        vm.onNameChange("Taxi")
        vm.onAmountChange("40")
        vm.onSave()
        advanceUntilIdle()
        val saved = repo.expenses.value.last()
        assertEquals(ME, saved.paidBy)
        assertEquals(listOf(ME), saved.splitBetween)
    }

    @Test
    fun newExpense_withTravelers_isPaidByMeAndSplitWithEveryone() = runTest(dispatcher) {
        withTravelers()
        val vm = vm()
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s.showSplit)
        assertEquals(listOf(ME, "noa", "amit"), s.people.map { it.key })
        assertEquals(ME, s.paidBy)
        assertEquals(listOf(ME, "noa", "amit"), s.splitBetween)
        assertFalse(s.isNotShared)
    }

    @Test
    fun notShared_setsTheSplitToThePayer_andFollowsAPayerChange() = runTest(dispatcher) {
        withTravelers()
        val vm = vm()
        advanceUntilIdle()
        vm.onNotShared()
        assertEquals(listOf(ME), vm.uiState.value.splitBetween)
        assertTrue(vm.uiState.value.isNotShared)
        vm.onPaidByChange("noa")
        assertEquals(listOf("noa"), vm.uiState.value.splitBetween)
        assertTrue(vm.uiState.value.isNotShared)
    }

    @Test
    fun changingThePayer_keepsASharedSplit() = runTest(dispatcher) {
        withTravelers()
        val vm = vm()
        advanceUntilIdle()
        vm.onPaidByChange("amit")
        assertEquals("amit", vm.uiState.value.paidBy)
        assertEquals(listOf(ME, "noa", "amit"), vm.uiState.value.splitBetween)
    }

    @Test
    fun splitToggle_keepsListOrder_andAnEmptySplitCantBeSaved() = runTest(dispatcher) {
        withTravelers()
        val vm = vm()
        advanceUntilIdle()
        vm.onNameChange("Pizza")
        vm.onAmountChange("90")
        listOf(ME, "noa", "amit").forEach(vm::onSplitToggle)
        assertTrue(vm.uiState.value.splitBetween.isEmpty())
        vm.onSave()
        advanceUntilIdle()
        assertEquals(ExpenseFieldError.SPLIT_EMPTY, vm.uiState.value.splitError)
        assertEquals(listOf(dinner), repo.expenses.value)

        vm.onSplitToggle("amit")
        vm.onSplitToggle(ME)
        assertNull(vm.uiState.value.splitError)
        assertEquals(listOf(ME, "amit"), vm.uiState.value.splitBetween)
        vm.onPaidByChange("noa")
        vm.onSave()
        advanceUntilIdle()
        val saved = repo.expenses.value.last()
        assertEquals("noa", saved.paidBy)
        assertEquals(listOf(ME, "amit"), saved.splitBetween)
        assertEquals(9_000L, saved.amountAgorot)
    }

    @Test
    fun editing_keepsTheStoredPayerAndSplit() = runTest(dispatcher) {
        withTravelers()
        repo.expenses.value = listOf(dinner.copy(paidBy = "amit", splitBetween = listOf("amit", ME)))
        val vm = vm(expenseId = 5)
        advanceUntilIdle()
        assertEquals("amit", vm.uiState.value.paidBy)
        assertEquals(listOf(ME, "amit"), vm.uiState.value.splitBetween) // list order
        vm.onNameChange("Dinner out")
        vm.onSave()
        advanceUntilIdle()
        val saved = repo.expenses.value.single()
        assertEquals("amit", saved.paidBy)
        assertEquals(listOf(ME, "amit"), saved.splitBetween)
        assertEquals(12_050L, saved.amountAgorot)
    }

    @Test
    fun editing_anExpenseOfARemovedTraveler_keepsThemAsRemoved() = runTest(dispatcher) {
        // Only possible through a sync conflict: "gone" was removed on another device.
        repo.expenses.value = listOf(dinner.copy(paidBy = "gone", splitBetween = listOf(ME, "gone")))
        val vm = vm(expenseId = 5)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s.showSplit)
        assertEquals(listOf(Person.You(ME), Person.Removed("gone")), s.people)
        assertEquals("gone", s.paidBy)
    }
}
