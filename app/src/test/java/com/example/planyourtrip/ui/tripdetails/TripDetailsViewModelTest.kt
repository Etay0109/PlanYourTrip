package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.FakeActivityRepository
import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeCurrencyRepository
import com.example.planyourtrip.FakeDestinationPhotoRepository
import com.example.planyourtrip.FakeExpenseRepository
import com.example.planyourtrip.FakeTravelerRepository
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.ME
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.currency.ExchangeRate
import com.example.planyourtrip.data.photo.DestinationPhoto
import com.example.planyourtrip.data.photo.PhotoQuery
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.DeletedActivity
import com.example.planyourtrip.data.trip.DeletedActivityStore
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.data.trip.TripPlace
import com.example.planyourtrip.ui.common.TripStatus
import com.example.planyourtrip.ui.common.split.Person
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
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
class TripDetailsViewModelTest {

    private val today = LocalDate.of(2026, 10, 5)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val dispatcher = UnconfinedTestDispatcher()

    private val rome = Trip(1, "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY)
    private val auth = FakeAuthRepository(AuthUser("u1", "Etay", "etay@example.com"))
    private lateinit var trips: FakeTripRepository
    private lateinit var expenses: FakeExpenseRepository
    private lateinit var currencies: FakeCurrencyRepository
    private lateinit var activities: FakeActivityRepository
    private lateinit var travelers: FakeTravelerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trips = FakeTripRepository(listOf("u1" to rome))
        expenses = FakeExpenseRepository(trips)
        currencies = FakeCurrencyRepository(now = clock::instant)
        activities = FakeActivityRepository(trips)
        travelers = FakeTravelerRepository(trips, expenses)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val deleted = DeletedActivityStore()

    private fun vm(tripId: Long = 1) =
        TripDetailsViewModel(tripId, trips, expenses, travelers, activities, auth, currencies, clock, dispatcher, deleted)

    private suspend fun TripDetailsViewModel.loaded() =
        uiState.filterIsInstance<TripDetailsUiState.Loaded>().first()

    @Test
    fun tripWithoutBudget_hasNoSummary() = runTest(dispatcher) {
        val state = vm().loaded()
        assertEquals(rome, state.trip)
        assertEquals(TripStatus.InDays(38), state.status)
        // The header leaves this year out of its dates.
        assertEquals(2026, state.currentYear)
        assertNull(state.summary)
        assertTrue(state.expenses.isEmpty())
        assertTrue(state.categoryTotals.isEmpty())
    }

    @Test
    fun budgetAndExpenses_areCombined_newestFirst() = runTest(dispatcher) {
        trips.trips.value = listOf("u1" to rome.copy(budgetAgorot = 600_000))
        expenses.expenses.value = listOf(
            Expense(1, 1, "Flights", 300_000, ExpenseCategory.FLIGHTS, LocalDate.of(2026, 9, 1), paidBy = ME, splitBetween = listOf(ME)),
            Expense(2, 1, "Dinner", 45_000, ExpenseCategory.FOOD, LocalDate.of(2026, 9, 20), paidBy = ME, splitBetween = listOf(ME)),
        )
        val state = vm().loaded()

        assertEquals(listOf(2L, 1L), state.expenses.map { it.id })
        assertEquals(255_000L, state.summary?.leftAgorot)
        assertEquals(PerDay(42_500, 6), state.summary?.perDay)
        assertEquals(listOf(ExpenseCategory.FLIGHTS, ExpenseCategory.FOOD), state.categoryTotals.map { it.category })
    }

    @Test
    fun budget_countsOnlyMyShare_andExpensesWithoutMeAreStillListed() = runTest(dispatcher) {
        trips.trips.value = listOf("u1" to rome.copy(budgetAgorot = 100_000))
        travelers.travelers.value = listOf(
            Traveler(1, 1, "noa", "Noa", TravelerColor.ROSE),
            Traveler(2, 1, "amit", "Amit", TravelerColor.INDIGO),
        )
        expenses.expenses.value = listOf(
            // ₪100 three ways: my share is ₪33.34 (the extra agora goes to "You", first in the list).
            Expense(
                1, 1, "Dinner", 10_000, ExpenseCategory.FOOD, LocalDate.of(2026, 9, 20),
                paidBy = "noa", splitBetween = listOf(ME, "noa", "amit"),
            ),
            // Not mine at all: ₪0 for me, but still in the list and in the legend as ₪0.
            Expense(
                2, 1, "Hotel", 50_000, ExpenseCategory.HOTELS, LocalDate.of(2026, 9, 21),
                paidBy = "amit", splitBetween = listOf("noa", "amit"),
            ),
            // Mine, not shared.
            Expense(3, 1, "SIM", 2_000, ExpenseCategory.OTHER, LocalDate.of(2026, 9, 22), paidBy = ME, splitBetween = listOf(ME)),
        )
        val state = vm().loaded()

        assertEquals(3, state.expenses.size)
        assertEquals(mapOf(1L to 3_334L, 2L to 0L, 3L to 2_000L), state.myShares)
        assertEquals(5_334L, state.summary?.spentAgorot)
        assertEquals(94_666L, state.summary?.leftAgorot)
        assertEquals(
            listOf(ExpenseCategory.FOOD to 3_334L, ExpenseCategory.OTHER to 2_000L, ExpenseCategory.HOTELS to 0L),
            state.categoryTotals.map { it.category to it.amountAgorot },
        )
    }

    @Test
    fun expenseRows_getPayerSplitAndMyShare_onlyWithOtherTravelers() = runTest(dispatcher) {
        trips.trips.value = listOf("u1" to rome)
        val noa = Traveler(1, 1, "noa", "Noa", TravelerColor.ROSE)
        val amit = Traveler(2, 1, "amit", "Amit", TravelerColor.INDIGO)
        expenses.expenses.value = listOf(
            // Stored in another order than the list: shown as You, Noa, Amit.
            Expense(
                1, 1, "Dinner", 10_000, ExpenseCategory.FOOD, LocalDate.of(2026, 9, 20),
                paidBy = "noa", splitBetween = listOf("amit", ME, "noa"),
            ),
            Expense(2, 1, "SIM", 2_000, ExpenseCategory.OTHER, LocalDate.of(2026, 9, 22), paidBy = ME, splitBetween = listOf(ME)),
        )
        val vm = vm()
        // Only me: no split line on any row.
        assertEquals(emptyMap<Long, Any>(), vm.loaded().expenseSplits)

        travelers.travelers.value = listOf(noa, amit)
        val splits = vm.loaded().expenseSplits
        assertEquals(Person.Named("noa", "Noa", TravelerColor.ROSE), splits.getValue(1).payer)
        assertEquals(listOf(ME, "noa", "amit"), splits.getValue(1).splitWith.map { it.key })
        assertEquals(3_334L, splits.getValue(1).myShareAgorot)
        assertEquals(Person.You(ME), splits.getValue(2).payer)
        assertEquals(listOf(Person.You(ME)), splits.getValue(2).splitWith)
        assertEquals(2_000L, splits.getValue(2).myShareAgorot)
    }

    @Test
    fun existingTrip_withoutTravelers_keepsExactlyTheSameNumbers() = runTest(dispatcher) {
        trips.trips.value = listOf("u1" to rome.copy(budgetAgorot = 600_000))
        // What the v13 migration made of every existing expense: paid by me, not shared ("me" is my uid since v14).
        expenses.expenses.value = listOf(
            Expense(1, 1, "Flights", 300_000, ExpenseCategory.FLIGHTS, LocalDate.of(2026, 9, 1), paidBy = ME, splitBetween = listOf(ME)),
            Expense(2, 1, "Dinner", 45_000, ExpenseCategory.FOOD, LocalDate.of(2026, 9, 20), paidBy = ME, splitBetween = listOf(ME)),
        )
        val state = vm().loaded()
        assertEquals(345_000L, state.summary?.spentAgorot)
        assertEquals(255_000L, state.summary?.leftAgorot)
        assertTrue(state.travelers.isEmpty())
    }

    @Test
    fun undo_bringsBackTheDeletedActivity() = runTest(dispatcher) {
        val visit = TripActivity(
            7, 1, rome.startDate, LocalTime.of(10, 0), "Arena tour", ActivityType.ACTIVITY, note = "Tickets",
            location = "Colosseum",
        )
        activities.activities.value = listOf(visit)
        val vm = vm()
        vm.deletedActivity.launchIn(backgroundScope)

        // The activity form deletes it and hands it over.
        deleted.offer(checkNotNull(activities.deleteActivity(visit.id, "u1")))
        assertTrue(activities.activities.value.isEmpty())
        val shown = checkNotNull(vm.deletedActivity.value)
        assertEquals(DeletedActivity(visit), shown)

        vm.onUndoDelete(shown)

        assertEquals(visit.copy(id = activities.activities.value.single().id), activities.activities.value.single())
        assertNull(vm.deletedActivity.value)
        assertNull(deleted.last.value)
    }

    @Test
    fun deletedMessage_onlyForThisTrip_andDismissingKeepsTheDelete() = runTest(dispatcher) {
        val visit = TripActivity(7, 1, rome.startDate, null, "Meet", ActivityType.OTHER)
        val elsewhere = DeletedActivity(visit.copy(id = 8, tripId = 2))
        val vm = vm()
        vm.deletedActivity.launchIn(backgroundScope)

        deleted.offer(elsewhere)
        assertNull(vm.deletedActivity.value)

        val mine = DeletedActivity(visit)
        deleted.offer(mine)
        assertEquals(mine, vm.deletedActivity.value)
        vm.onDeleteMessageDismissed(mine)

        assertNull(vm.deletedActivity.value)
        assertTrue(activities.activities.value.isEmpty())
    }

    @Test
    fun someoneElsesTrip_isNotFound() = runTest(dispatcher) {
        trips.trips.value = listOf("other" to rome)
        val vm = vm()
        vm.uiState.launchIn(backgroundScope)
        assertEquals(TripDetailsUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun setBudget_validatesAndStores() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.launchIn(backgroundScope)

        vm.onEditBudget()
        assertEquals(BudgetDialogState(input = ""), vm.budgetDialog.value)

        vm.onSaveBudget()
        assertEquals(BudgetFieldError.EMPTY, vm.budgetDialog.value?.error)

        vm.onBudgetInputChange("0")
        assertNull(vm.budgetDialog.value?.error) // typing clears the error
        vm.onSaveBudget()
        assertEquals(BudgetFieldError.INVALID, vm.budgetDialog.value?.error)

        vm.onBudgetInputChange("abc")
        vm.onSaveBudget()
        assertEquals(BudgetFieldError.INVALID, vm.budgetDialog.value?.error)

        vm.onBudgetInputChange("6000.50")
        vm.onSaveBudget()
        assertNull(vm.budgetDialog.value)
        assertEquals(600_050L, trips.tripsOf("u1").single().budgetAgorot)
        assertEquals(600_050L, (vm.uiState.value as TripDetailsUiState.Loaded).summary?.totalAgorot)
    }

    @Test
    fun editBudget_prefillsCurrentValue_andDismissKeepsIt() = runTest(dispatcher) {
        trips.trips.value = listOf("u1" to rome.copy(budgetAgorot = 600_000))
        val vm = vm()
        vm.uiState.launchIn(backgroundScope)

        vm.onEditBudget()
        assertEquals("6000", vm.budgetDialog.value?.input)
        vm.onBudgetInputChange("1")
        vm.onDismissBudget()
        assertNull(vm.budgetDialog.value)
        assertEquals(600_000L, trips.tripsOf("u1").single().budgetAgorot)
    }

    // ---------- Currency card ----------

    private val rateDate = LocalDate.of(2026, 10, 2)

    private fun withCurrency(code: String?) {
        trips.trips.value = listOf("u1" to rome.copy(currencyCode = code))
    }

    private fun cached(code: String, rate: String, age: Duration) {
        currencies.cache.value += code to ExchangeRate(code, BigDecimal(rate), rateDate, clock.instant() - age)
    }

    /** A view model whose card is collected, as the screen does. */
    private fun kotlinx.coroutines.test.TestScope.collectedVm(): TripDetailsViewModel = vm().also {
        it.uiState.launchIn(backgroundScope)
        it.currencyCard.launchIn(backgroundScope)
    }

    @Test
    fun noCurrency_showsChooseCurrency_andDoesNotFetch() = runTest(dispatcher) {
        val vm = collectedVm()
        vm.onOverviewShown()
        assertEquals(CurrencyCardState.NoCurrency, vm.currencyCard.value)
        assertTrue(currencies.refreshCalls.isEmpty())
    }

    @Test
    fun choosingACurrency_savesItOnTheTrip_andShowsTheRate() = runTest(dispatcher) {
        currencies.nextRates["EUR"] = BigDecimal("3.4408") to rateDate
        val vm = collectedVm()

        vm.onChooseCurrency()
        assertEquals(listOf("EUR", "USD"), vm.currencyPicker.value?.results?.map { it.code })
        vm.onCurrencyQueryChange("dollar")
        assertEquals(listOf("USD"), vm.currencyPicker.value?.results?.map { it.code })

        vm.onCurrencySelected("EUR")
        assertNull(vm.currencyPicker.value)
        assertEquals("EUR", trips.tripsOf("u1").single().currencyCode)
        assertEquals(listOf("EUR"), currencies.refreshCalls) // fetched right away

        vm.onOverviewShown() // the screen calls this when the code changes: no second request
        assertEquals(listOf("EUR"), currencies.refreshCalls)
        val card = vm.currencyCard.value as CurrencyCardState.Rate
        assertEquals("EUR", card.code)
        assertEquals(BigDecimal("3.4408"), card.ilsPerUnit)
        assertEquals(rateDate, card.date)
    }

    @Test
    fun currencyList_offline_showsRetry() = runTest(dispatcher) {
        currencies.offline = true
        val vm = collectedVm()
        vm.onChooseCurrency()
        assertEquals(true, vm.currencyPicker.value?.failed)

        currencies.offline = false
        vm.onRetryCurrencies()
        assertEquals(false, vm.currencyPicker.value?.failed)
        assertEquals(2, vm.currencyPicker.value?.results?.size)
    }

    @Test
    fun freshCache_isNotRefreshed() = runTest(dispatcher) {
        withCurrency("EUR")
        cached("EUR", "3.40", Duration.ofHours(11))
        val vm = collectedVm()
        vm.onOverviewShown()
        assertTrue(currencies.refreshCalls.isEmpty())
        assertEquals(BigDecimal("3.40"), (vm.currencyCard.value as CurrencyCardState.Rate).ilsPerUnit)
    }

    @Test
    fun staleCache_isRefreshed() = runTest(dispatcher) {
        withCurrency("EUR")
        cached("EUR", "3.40", Duration.ofHours(12))
        currencies.nextRates["EUR"] = BigDecimal("3.4408") to LocalDate.of(2026, 10, 5)
        val vm = collectedVm()
        vm.onOverviewShown()
        assertEquals(listOf("EUR"), currencies.refreshCalls)
        val card = vm.currencyCard.value as CurrencyCardState.Rate
        assertEquals(BigDecimal("3.4408"), card.ilsPerUnit)
        assertEquals(LocalDate.of(2026, 10, 5), card.date)
    }

    @Test
    fun offline_withCache_showsTheCachedRateAndItsDate() = runTest(dispatcher) {
        withCurrency("EUR")
        cached("EUR", "3.40", Duration.ofDays(2))
        currencies.offline = true
        val vm = collectedVm()
        vm.onOverviewShown()
        assertEquals(listOf("EUR"), currencies.refreshCalls)
        val card = vm.currencyCard.value as CurrencyCardState.Rate
        assertEquals(BigDecimal("3.40"), card.ilsPerUnit)
        assertEquals(rateDate, card.date)
    }

    @Test
    fun offline_withoutCache_isUnavailable_andTryAgainRecovers() = runTest(dispatcher) {
        withCurrency("EUR")
        currencies.offline = true
        currencies.nextRates["EUR"] = BigDecimal("3.4408") to rateDate
        val vm = collectedVm()
        assertEquals(CurrencyCardState.Loading("EUR"), vm.currencyCard.value)

        vm.onOverviewShown()
        assertEquals(CurrencyCardState.Unavailable("EUR"), vm.currencyCard.value)

        currencies.offline = false
        vm.onRetryRate()
        assertTrue(vm.currencyCard.value is CurrencyCardState.Rate)
    }

    @Test
    fun shekelTrip_hidesTheConverter_andDoesNotFetch() = runTest(dispatcher) {
        withCurrency("ILS")
        val vm = collectedVm()
        vm.onOverviewShown()
        assertEquals(CurrencyCardState.Shekel, vm.currencyCard.value)
        assertTrue(currencies.refreshCalls.isEmpty())
    }

    @Test
    fun changingCurrencyOnTheCard_fetchesTheNewRate_evenWithAFreshCache() = runTest(dispatcher) {
        withCurrency("EUR")
        cached("EUR", "3.40", Duration.ZERO)
        cached("USD", "3.70", Duration.ofHours(1))
        currencies.nextRates["USD"] = BigDecimal("3.7512") to rateDate
        val vm = collectedVm()
        vm.onOverviewShown()
        assertTrue(currencies.refreshCalls.isEmpty())

        vm.onCurrencySelected("USD")
        assertEquals(listOf("USD"), currencies.refreshCalls)
        assertEquals(BigDecimal("3.7512"), (vm.currencyCard.value as CurrencyCardState.Rate).ilsPerUnit)
    }

    @Test
    fun currencyChangedInTheTripForm_isFetchedWhenTheOverviewShowsIt() = runTest(dispatcher) {
        withCurrency("EUR")
        cached("EUR", "3.40", Duration.ZERO)
        cached("CHF", "4.10", Duration.ofHours(1))
        currencies.nextRates["CHF"] = BigDecimal("4.2101") to rateDate
        val vm = collectedVm()
        vm.onOverviewShown()

        withCurrency("CHF") // saved by the trip form
        vm.onOverviewShown()
        assertEquals(listOf("CHF"), currencies.refreshCalls)
        assertEquals(BigDecimal("4.2101"), (vm.currencyCard.value as CurrencyCardState.Rate).ilsPerUnit)

        vm.onOverviewShown() // same currency again: the fresh cache is enough
        assertEquals(listOf("CHF"), currencies.refreshCalls)
    }

    @Test
    fun openingTheOverview_firstTime_usesTheCacheWhenFresh() = runTest(dispatcher) {
        withCurrency("EUR")
        cached("EUR", "3.40", Duration.ofHours(1))
        val vm = collectedVm()
        vm.onOverviewShown()
        vm.onOverviewShown()
        assertTrue(currencies.refreshCalls.isEmpty())
    }

    // ---------- Itinerary ----------

    private fun nov(day: Int) = LocalDate.of(2026, 11, day)

    private fun activity(id: Long, date: LocalDate, time: LocalTime? = null, tripId: Long = 1) =
        TripActivity(id, tripId, date, time, "Activity $id", ActivityType.ACTIVITY)

    @Test
    fun noActivities_itineraryIsEmpty_andStartsOnTheFirstDay() = runTest(dispatcher) {
        val itinerary = vm().loaded().itinerary
        assertFalse(itinerary.hasActivities)
        assertEquals(6, itinerary.days.size)
        assertEquals(nov(12), itinerary.plan.day)
        assertEquals(DayTitle.FirstDay, itinerary.overviewDay.title)
    }

    @Test
    fun activities_areShownByDay_withProgress() = runTest(dispatcher) {
        activities.activities.value = listOf(
            activity(1, nov(12), LocalTime.of(13, 0)),
            activity(2, nov(12)),
            activity(3, nov(12), LocalTime.of(9, 0)),
            activity(4, nov(14)),
            activity(5, nov(20)), // outside the trip
        )
        val itinerary = vm().loaded().itinerary
        assertTrue(itinerary.hasActivities)
        assertEquals(listOf(3L, 1L, 2L), itinerary.plan.activities.map { it.id })
        assertEquals(2, itinerary.progress.plannedDays)
        assertEquals(listOf(5L), itinerary.outside.map { it.id })
    }

    @Test
    fun duringTheTrip_todayIsSelected() = runTest(dispatcher) {
        trips.trips.value = listOf("u1" to rome.copy(startDate = today.minusDays(1), endDate = today.plusDays(3)))
        val itinerary = vm().loaded().itinerary
        assertEquals(today, itinerary.plan.day)
        assertEquals(today, itinerary.overviewDay.day)
        assertEquals(DayTitle.Today, itinerary.overviewDay.title)
    }

    @Test
    fun overviewArrows_moveBetweenDays_andStopAtTheEnds() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.launchIn(backgroundScope)

        vm.onOverviewPreviousDay() // already on the first day
        assertEquals(nov(12), vm.loaded().itinerary.overviewDay.day)

        vm.onOverviewNextDay()
        vm.onOverviewNextDay()
        val shown = vm.loaded().itinerary.overviewDay
        assertEquals(nov(14), shown.day)
        assertEquals(DayTitle.DayNumber(3), shown.title)

        repeat(10) { vm.onOverviewNextDay() }
        assertEquals(nov(17), vm.loaded().itinerary.overviewDay.day)
        assertFalse(vm.loaded().itinerary.overviewDay.hasNext)
        // The Plan tab keeps its own day.
        assertEquals(nov(12), vm.loaded().itinerary.plan.day)
    }

    @Test
    fun seeFullPlan_opensThePlanOnTheOverviewDay() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.launchIn(backgroundScope)
        vm.onOverviewNextDay()
        vm.onSeeFullPlan()
        assertEquals(nov(13), vm.loaded().itinerary.plan.day)
    }

    @Test
    fun planDay_fallsBackToTheDefault_whenTheDatesNoLongerContainIt() = runTest(dispatcher) {
        activities.activities.value = listOf(activity(1, nov(16)))
        val vm = vm()
        vm.uiState.launchIn(backgroundScope)
        vm.onPlanDaySelected(nov(16))
        assertEquals(nov(16), vm.loaded().itinerary.plan.day)

        // The trip is shortened in the trip form: the activity is kept, but listed outside the dates.
        trips.trips.value = listOf("u1" to rome.copy(endDate = nov(14)))
        val itinerary = vm.loaded().itinerary
        assertEquals(nov(12), itinerary.plan.day)
        assertEquals(listOf(1L), itinerary.outside.map { it.id })
        assertEquals(listOf(1L), activities.activities.value.map { it.id })
    }

    @Test
    fun someoneElsesActivities_areNeverShown() = runTest(dispatcher) {
        val other = Trip(2, "Paris", nov(12), nov(17), TripColor.MINT)
        trips.trips.value = listOf("u1" to rome, "other" to other)
        activities.activities.value = listOf(activity(1, nov(12), tripId = 2))
        assertFalse(vm().loaded().itinerary.hasActivities)
    }

    @Test
    fun headerPhoto_followsTheTripsDestination() = runTest(dispatcher) {
        val photos = FakeDestinationPhotoRepository()
        val vm = TripDetailsViewModel(
            1, trips, expenses, travelers, activities, auth, currencies, clock, dispatcher, deleted,
            photoRepository = photos,
        )
        backgroundScope.launch { vm.headerPhoto.collect {} }
        val romeQuery = PhotoQuery(rome.destination)

        // No photo yet: the gradient only.
        assertNull(vm.headerPhoto.value)
        assertEquals(listOf(romeQuery), photos.requested)

        val photo = DestinationPhoto("https://img.test/r-960.jpg", "https://img.test/r-250.jpg")
        photos.photos.value = mapOf(romeQuery.key to photo)
        assertEquals(photo, vm.headerPhoto.value)

        // The destination is edited: the new one is looked up and the old photo goes.
        trips.updateTrip(1, "u1", "Lisbon", rome.startDate, rome.endDate, null, TripPlace.NONE)
        assertEquals(PhotoQuery("Lisbon"), photos.requested.last())
        assertNull(vm.headerPhoto.value)
    }
}
