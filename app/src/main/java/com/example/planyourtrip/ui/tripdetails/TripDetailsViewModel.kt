package com.example.planyourtrip.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.currency.CurrencyRepository
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.data.currency.isStale
import com.example.planyourtrip.data.trip.ActivityRepository
import com.example.planyourtrip.data.trip.DeletedActivity
import com.example.planyourtrip.data.trip.DeletedActivityStore
import com.example.planyourtrip.data.trip.ExpenseRepository
import com.example.planyourtrip.data.trip.TravelerRepository
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.common.CurrencyPickerState
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.common.categoryTotals
import com.example.planyourtrip.ui.common.split.myShare
import com.example.planyourtrip.ui.common.split.travelerOrder
import com.example.planyourtrip.ui.common.tripStatus
import com.example.planyourtrip.ui.navigation.TripDetails
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TripDetailsViewModel(
    private val tripId: Long,
    private val tripRepository: TripRepository,
    expenseRepository: ExpenseRepository,
    travelerRepository: TravelerRepository,
    private val activityRepository: ActivityRepository,
    private val authRepository: AuthRepository,
    private val currencyRepository: CurrencyRepository,
    private val clock: Clock,
    /** Where budget totals are computed; tests pass their test dispatcher. */
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** The last deleted activity (from the activity form): "Activity deleted" with "Undo". */
    private val deletedActivities: DeletedActivityStore = DeletedActivityStore(),
) : ViewModel() {

    /** Day chosen on the Plan tab; null = the default day (first day, or today during the trip). */
    private val planDay = MutableStateFlow<LocalDate?>(null)

    /** Day shown in the Overview day section; null = the default day. */
    private val overviewDay = MutableStateFlow<LocalDate?>(null)

    /** The trip with its expenses, activities, budget and itinerary math, if it belongs to the signed-in user. */
    val uiState: StateFlow<TripDetailsUiState> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(TripDetailsUiState.NotFound)
            combine(
                tripRepository.observeTrip(tripId, user.uid),
                combine(
                    expenseRepository.observeExpenses(tripId, user.uid),
                    travelerRepository.observeTravelers(tripId, user.uid),
                    ::Pair,
                ),
                activityRepository.observeActivities(tripId, user.uid),
                planDay,
                overviewDay,
            ) { trip, (expenses, travelers), activities, planSelection, overviewSelection ->
                if (trip == null) return@combine TripDetailsUiState.NotFound
                // The budget counts only my share of each expense.
                val order = travelerOrder(travelers)
                val myShares = expenses.associate { it.id to it.myShare(order) }
                val today = LocalDate.now(clock)
                val itinerary = itineraryState(
                    trip.startDate, trip.endDate, activities, planSelection, overviewSelection, today,
                )
                TripDetailsUiState.Loaded(
                    trip = trip,
                    status = tripStatus(trip, today),
                    summary = budgetSummary(
                        trip.budgetAgorot, myShares.values.sum(), trip.startDate, trip.endDate, today,
                    ),
                    categoryTotals = categoryTotals(
                        expenses, amountOf = { myShares.getValue(it.id) }, includeZeroCategories = true,
                    ),
                    expenses = expenses,
                    travelers = travelers,
                    myShares = myShares,
                    itinerary = itinerary,
                )
            }
        }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TripDetailsUiState.Loading)

    /** An activity of this trip that was just deleted: the screen shows "Activity deleted" with "Undo". */
    val deletedActivity: StateFlow<DeletedActivity?> = deletedActivities.last
        .map { it?.takeIf { deleted -> deleted.activity.tripId == tripId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** "Undo": the activity comes back. */
    fun onUndoDelete(deleted: DeletedActivity) {
        deletedActivities.clear(deleted)
        val ownerId = authRepository.currentUser.value?.uid ?: return
        viewModelScope.launch { activityRepository.restoreActivity(deleted, ownerId) }
    }

    /** The message went away without "Undo": the delete stays. */
    fun onDeleteMessageDismissed(deleted: DeletedActivity) = deletedActivities.clear(deleted)

    private val _budgetDialog = MutableStateFlow<BudgetDialogState?>(null)

    /** The open "Set budget" dialog, or null when closed. */
    val budgetDialog: StateFlow<BudgetDialogState?> = _budgetDialog.asStateFlow()

    /** Opens the dialog, prefilled with the current budget (empty when none). */
    fun onEditBudget() {
        val current = (uiState.value as? TripDetailsUiState.Loaded)?.trip?.budgetAgorot
        _budgetDialog.value = BudgetDialogState(input = current?.let(Money::toInput).orEmpty())
    }

    fun onBudgetInputChange(value: String) =
        _budgetDialog.update { it?.copy(input = value, error = null) }

    fun onDismissBudget() {
        _budgetDialog.value = null
    }

    /** Validates (a number greater than zero), stores the budget and closes the dialog. */
    fun onSaveBudget() {
        val dialog = _budgetDialog.value ?: return
        val error = validateBudget(dialog.input)
        if (error != null) {
            _budgetDialog.value = dialog.copy(error = error)
            return
        }
        val ownerId = authRepository.currentUser.value?.uid ?: return
        val agorot = checkNotNull(Money.parseAgorot(dialog.input))
        _budgetDialog.value = null
        viewModelScope.launch { tripRepository.setBudget(tripId, ownerId, agorot) }
    }

    // ---------- Itinerary ----------

    private fun loadedItinerary(): ItineraryState? = (uiState.value as? TripDetailsUiState.Loaded)?.itinerary

    /** A day chip on the Plan tab. */
    fun onPlanDaySelected(day: LocalDate) {
        planDay.value = day
    }

    /** "<" in the Overview day section; does nothing on the first day. */
    fun onOverviewPreviousDay() {
        val shown = loadedItinerary()?.overviewDay ?: return
        if (shown.hasPrevious) overviewDay.value = shown.day.minusDays(1)
    }

    /** ">" in the Overview day section; does nothing on the last day. */
    fun onOverviewNextDay() {
        val shown = loadedItinerary()?.overviewDay ?: return
        if (shown.hasNext) overviewDay.value = shown.day.plusDays(1)
    }

    /** "See full plan": the Plan tab opens on the day shown in the Overview (the screen switches the tab). */
    fun onSeeFullPlan() {
        loadedItinerary()?.let { planDay.value = it.overviewDay.day }
    }

    // ---------- Currency card ----------

    /** Currency code of the loaded trip; null inside [Selected] = no currency chosen yet. */
    private data class Selected(val code: String?)

    /** Code whose last refresh failed, so the card can tell "loading" from "unavailable". */
    private val refreshFailedFor = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null

    /** The currency the Overview last showed, to notice a change (made on the card or in the trip form). */
    private var shownCode: String? = null
    private var hasShown = false

    /**
     * The Overview currency card; null until the trip is loaded (nothing is drawn). The converter's text is
     * not here: it lives in the card, so typing never waits for this flow.
     */
    val currencyCard: StateFlow<CurrencyCardState?> = uiState
        .map { state -> (state as? TripDetailsUiState.Loaded)?.let { Selected(it.trip.currencyCode) } }
        .distinctUntilChanged()
        .flatMapLatest { selected ->
            val code = selected?.code
            when {
                selected == null -> flowOf(null)
                code == null -> flowOf(CurrencyCardState.NoCurrency)
                code == ILS -> flowOf(CurrencyCardState.Shekel)
                else -> combine(currencyRepository.observeRate(code), refreshFailedFor) { rate, failedFor ->
                    when {
                        rate != null -> CurrencyCardState.Rate(code, rate.ilsPerUnit, rate.date)
                        failedFor == code -> CurrencyCardState.Unavailable(code)
                        else -> CurrencyCardState.Loading(code)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun currentCurrency(): String? = (uiState.value as? TripDetailsUiState.Loaded)?.trip?.currencyCode

    /**
     * Called whenever the Overview is shown or the trip's currency changes. A currency different from the one
     * shown before is fetched right away; otherwise the rate is refreshed only when 12 hours old or more.
     */
    fun onOverviewShown() {
        val code = currentCurrency()
        val changed = hasShown && code != shownCode
        hasShown = true
        shownCode = code
        if (code != null) refreshRate(code, force = changed)
    }

    /** "Try again" on the card. */
    fun onRetryRate() {
        currentCurrency()?.let { refreshRate(it, force = true) }
    }

    private fun refreshRate(code: String, force: Boolean) {
        if (code == ILS) return
        if (refreshJob?.isActive == true) {
            if (!force) return
            refreshJob?.cancel()
        }
        refreshJob = viewModelScope.launch {
            if (!force) {
                val cached = currencyRepository.observeRate(code).first()
                if (!isStale(cached?.fetchedAt, clock.instant())) return@launch
            }
            refreshFailedFor.value = null
            val ok = currencyRepository.refreshRate(code)
            refreshFailedFor.value = if (ok) null else code
        }
    }

    // ---------- Currency picker ----------

    private val _currencyPicker = MutableStateFlow<CurrencyPickerState?>(null)

    /** The open currency picker, or null when closed. */
    val currencyPicker: StateFlow<CurrencyPickerState?> = _currencyPicker.asStateFlow()

    /** "Choose currency" or a tap on the currency chip: opens the picker and loads the list. */
    fun onChooseCurrency() {
        _currencyPicker.value = CurrencyPickerState(selectedCode = currentCurrency())
        loadCurrencies()
    }

    fun onRetryCurrencies() = loadCurrencies()

    private fun loadCurrencies() {
        _currencyPicker.update { it?.loading() }
        viewModelScope.launch {
            val list = currencyRepository.currencies()
            _currencyPicker.update { it?.loaded(list) }
        }
    }

    fun onCurrencyQueryChange(query: String) = _currencyPicker.update { it?.withQuery(query) }

    fun onDismissCurrencyPicker() {
        _currencyPicker.value = null
    }

    /**
     * Saves [code] on the trip, closes the picker and fetches the new rate right away. The card clears its
     * converter fields itself because they are keyed by the currency code.
     */
    fun onCurrencySelected(code: String) {
        val ownerId = authRepository.currentUser.value?.uid ?: return
        _currencyPicker.value = null
        if (code == currentCurrency()) return
        refreshFailedFor.value = null
        hasShown = true
        shownCode = code
        viewModelScope.launch { tripRepository.setCurrency(tripId, ownerId, code) }
        refreshRate(code, force = true)
    }

    companion object {
        /** Null when [input] is a valid budget: a number of shekels greater than zero. */
        fun validateBudget(input: String): BudgetFieldError? {
            if (input.isBlank()) return BudgetFieldError.EMPTY
            val agorot = Money.parseAgorot(input)
            return if (agorot == null || agorot <= 0) BudgetFieldError.INVALID else null
        }

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                val container = appContainer()
                TripDetailsViewModel(
                    tripId = handle.toRoute<TripDetails>().tripId,
                    tripRepository = container.tripRepository,
                    expenseRepository = container.expenseRepository,
                    travelerRepository = container.travelerRepository,
                    activityRepository = container.activityRepository,
                    authRepository = container.authRepository,
                    currencyRepository = container.currencyRepository,
                    clock = container.clock,
                    deletedActivities = container.deletedActivities,
                )
            }
        }
    }
}
