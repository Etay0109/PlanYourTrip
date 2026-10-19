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
import com.example.planyourtrip.data.photo.DestinationPhoto
import com.example.planyourtrip.data.photo.DestinationPhotoRepository
import com.example.planyourtrip.data.trip.ActivityRepository
import com.example.planyourtrip.data.trip.DeletedActivity
import com.example.planyourtrip.data.trip.DeletedActivityStore
import com.example.planyourtrip.data.trip.ExpenseRepository
import com.example.planyourtrip.data.trip.TravelerRepository
import com.example.planyourtrip.data.trip.TripMemberRepository
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.common.CurrencyPickerState
import com.example.planyourtrip.ui.common.WhileUiSubscribed
import com.example.planyourtrip.ui.common.tripStatus
import com.example.planyourtrip.ui.navigation.TripDetails
import com.example.planyourtrip.ui.tripdetails.members.membersState
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The trip screen's ViewModel. It observes the trip's data once ([observeTripData]) and hands it to its parts, each
 * with one job: the budget numbers ([BudgetCache]) and the budget dialog ([BudgetDialogController]), the itinerary
 * days ([ItinerarySelection]), the currency card and picker ([CurrencyCardController]) and the [headerPhoto].
 */
class TripDetailsViewModel(
    private val tripId: Long,
    tripRepository: TripRepository,
    expenseRepository: ExpenseRepository,
    travelerRepository: TravelerRepository,
    private val activityRepository: ActivityRepository,
    private val authRepository: AuthRepository,
    currencyRepository: CurrencyRepository,
    private val clock: Clock,
    /** Where budget totals are computed; tests pass their test dispatcher. */
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** The last deleted activity (from the activity form): "Activity deleted" with "Undo". */
    private val deletedActivities: DeletedActivityStore = DeletedActivityStore(),
    /** Names of the trip's members (shared trips). */
    memberRepository: TripMemberRepository = TripMemberRepository.None,
    /** The header's destination photo (this device only). */
    photoRepository: DestinationPhotoRepository = DestinationPhotoRepository.None,
    /** Keeps an open budget dialog across process death. */
    savedState: SavedStateHandle? = null,
) : ViewModel() {

    private val tripData = observeTripData(
        tripId, authRepository, tripRepository, expenseRepository, travelerRepository, memberRepository,
        activityRepository, computeDispatcher, viewModelScope,
    )

    private fun loaded(): TripDetailsUiState.Loaded? = uiState.value as? TripDetailsUiState.Loaded

    private val itinerary = ItinerarySelection(current = { loaded()?.itinerary })
    private val budget = BudgetCache()

    /** The trip with its expenses, activities, budget and itinerary math, if it belongs to the signed-in user. */
    val uiState: StateFlow<TripDetailsUiState> = combine(
        tripData, itinerary.planDay, itinerary.overviewDay,
    ) { data, planSelection, overviewSelection ->
        if (data == null) return@combine TripDetailsUiState.NotFound
        val trip = data.trip
        val today = LocalDate.now(clock)
        // A day chosen on the Plan tab or in the Overview only builds the itinerary again; the budget is reused.
        val money = budget.of(data, today)
        TripDetailsUiState.Loaded(
            trip = trip,
            status = tripStatus(trip, today),
            currentYear = today.year,
            summary = money.summary,
            categoryTotals = money.categoryTotals,
            expenses = data.expenses,
            travelers = data.travelers,
            myShares = money.myShares,
            expenseSplits = money.expenseSplits,
            itinerary = itineraryState(
                trip.startDate, trip.endDate, data.activities, planSelection, overviewSelection, today,
            ),
            members = membersState(data.uid, trip, data.members, clock.zone),
        )
    }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, WhileUiSubscribed, TripDetailsUiState.Loading)

    /** The header's destination photo; null while there is none (the gradient only). */
    val headerPhoto: StateFlow<DestinationPhoto?> = headerPhoto(tripData, photoRepository, viewModelScope)

    // ---------- Deleted activity ----------

    /** An activity of this trip that was just deleted: the screen shows "Activity deleted" with "Undo". */
    val deletedActivity: StateFlow<DeletedActivity?> = deletedActivities.last
        .map { it?.takeIf { deleted -> deleted.activity.tripId == tripId } }
        .stateIn(viewModelScope, WhileUiSubscribed, null)

    /** "Undo": the activity comes back. */
    fun onUndoDelete(deleted: DeletedActivity) {
        deletedActivities.clear(deleted)
        val ownerId = authRepository.currentUser.value?.uid ?: return
        viewModelScope.launch { activityRepository.restoreActivity(deleted, ownerId) }
    }

    /** The message went away without "Undo": the delete stays. */
    fun onDeleteMessageDismissed(deleted: DeletedActivity) = deletedActivities.clear(deleted)

    // ---------- Budget dialog ----------

    private val budgetDialogController = BudgetDialogController(
        tripId, tripRepository, authRepository, viewModelScope, savedState,
        currentBudget = { loaded()?.trip?.budgetAgorot },
    )

    /** The open "Set budget" dialog, or null when closed. */
    val budgetDialog: StateFlow<BudgetDialogState?> = budgetDialogController.state

    fun onEditBudget() = budgetDialogController.onEdit()

    fun onBudgetInputChange(value: String) = budgetDialogController.onInputChange(value)

    fun onDismissBudget() = budgetDialogController.onDismiss()

    fun onSaveBudget() = budgetDialogController.onSave()

    // ---------- Itinerary ----------

    fun onPlanDaySelected(day: LocalDate) = itinerary.onPlanDaySelected(day)

    fun onOverviewPreviousDay() = itinerary.onOverviewPreviousDay()

    fun onOverviewNextDay() = itinerary.onOverviewNextDay()

    fun onSeeFullPlan() = itinerary.onSeeFullPlan()

    // ---------- Currency card and picker ----------

    private val currency = CurrencyCardController(
        tripId, tripData, tripRepository, authRepository, currencyRepository, clock, viewModelScope,
        currentCurrency = { loaded()?.trip?.currencyCode },
    )

    /** The Overview currency card; null until the trip is loaded. */
    val currencyCard: StateFlow<CurrencyCardState?> = currency.card

    fun onOverviewShown() = currency.onOverviewShown()

    fun onRetryRate() = currency.onRetryRate()

    /** The open currency picker, or null when closed. */
    val currencyPicker: StateFlow<CurrencyPickerState?> = currency.picker

    fun onChooseCurrency() = currency.onChooseCurrency()

    fun onRetryCurrencies() = currency.onRetryCurrencies()

    fun onCurrencyQueryChange(query: String) = currency.onQueryChange(query)

    fun onDismissCurrencyPicker() = currency.onDismissPicker()

    fun onCurrencySelected(code: String) = currency.onCurrencySelected(code)

    companion object {
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
                    memberRepository = container.tripMemberRepository,
                    photoRepository = container.destinationPhotoRepository,
                    savedState = handle,
                )
            }
        }
    }
}
