package com.example.planyourtrip.ui.tripdetails.expense

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
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ExpenseInput
import com.example.planyourtrip.data.trip.ExpenseRepository
import com.example.planyourtrip.data.trip.TravelerRepository
import com.example.planyourtrip.data.trip.TripMemberRepository
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.common.split.inListOrder
import com.example.planyourtrip.ui.common.split.personOf
import com.example.planyourtrip.ui.common.split.travelerOrder
import com.example.planyourtrip.ui.navigation.ExpenseForm
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * New expense of [tripId] when [expenseId] is null; otherwise edits (or deletes) that expense. The amount can be
 * typed in shekels or in the trip currency; a foreign amount is converted with the latest cached rate on save.
 * With other travelers, "Paid by" and "Split between" say who paid and who shares it (equally).
 */
class ExpenseFormViewModel(
    private val tripId: Long,
    private val expenseId: Long?,
    private val repository: ExpenseRepository,
    private val tripRepository: TripRepository,
    private val currencyRepository: CurrencyRepository,
    private val authRepository: AuthRepository,
    private val clock: Clock,
    private val travelerRepository: TravelerRepository,
    /** Names of the trip's other members (shared trips). */
    private val memberRepository: TripMemberRepository = TripMemberRepository.None,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExpenseFormUiState(isEditing = expenseId != null, date = LocalDate.now(clock))
    )
    val uiState: StateFlow<ExpenseFormUiState> = _uiState.asStateFlow()

    /** The edited expense as loaded, so an unchanged amount keeps its stored rate. */
    private var stored: Expense? = null

    init {
        load()
    }

    /** Reads the trip currency and, when editing, fills the fields once with the expense's values. */
    private fun load() {
        val ownerId = authRepository.currentUser.value?.uid ?: return
        viewModelScope.launch {
            val trip = tripRepository.observeTrip(tripId, ownerId).first()
            val expense = expenseId?.let { repository.observeExpense(it, ownerId).first() }
            val travelers = travelerRepository.observeTravelers(tripId, ownerId).first()
            val members = memberRepository.observeMembers(tripId, ownerId).first()
            stored = expense
            val memberIds = trip?.memberIds.orEmpty()
            val order = travelerOrder(ownerId, memberIds, travelers)
            // An edited expense keeps its payer and split, even a traveler removed (or a member who left) since.
            val usedKeys = expense?.let { listOf(it.paidBy) + it.splitBetween }.orEmpty()
            val keys = inListOrder(order + usedKeys, order)
            // Shown with "You" first (the list order puts the trip's owner first).
            val people = keys.map { personOf(it, ownerId, travelers, memberIds, members) }.sortedBy { it !is Person.You }
            // Editing keeps the expense's own currency even if the trip currency changed since.
            val foreignCode = expense?.original?.currencyCode ?: trip?.currencyCode?.takeIf { it != ILS }
            _uiState.update { state ->
                val base = state.copy(
                    isLoading = false,
                    tripEnd = trip?.endDate,
                    foreignCode = foreignCode,
                    foreignRate = if (foreignCode != null) ForeignRateState.Loading else null,
                    // A new expense starts in the trip currency when there is one.
                    currencyCode = foreignCode ?: ILS,
                    people = people,
                    order = keys,
                )
                if (expense == null) {
                    // Paid by me, split between everyone.
                    base.copy(
                        date = ExpenseValidator.defaultDate(state.date, trip?.endDate),
                        paidBy = ownerId,
                        splitBetween = order,
                    )
                } else {
                    val entered = ExpenseCurrency.entered(expense)
                    base.copy(
                        name = expense.name,
                        amount = entered.text,
                        currencyCode = entered.currencyCode,
                        category = expense.category,
                        date = expense.date,
                        // Dated after the trip (its dates were shortened later): kept, but flagged to fix.
                        dateError = ExpenseValidator.validateDate(expense.date, trip?.endDate),
                        paidBy = expense.paidBy,
                        splitBetween = inListOrder(expense.splitBetween, order),
                    )
                }.withPreview()
            }
            if (foreignCode != null) watchRate(foreignCode)
        }
    }

    /** Follows the cached rate for the whole form, and refreshes it once if it is missing or 12+ hours old. */
    private fun watchRate(code: String) {
        viewModelScope.launch {
            currencyRepository.observeRate(code).collect { rate ->
                if (rate != null) {
                    _uiState.update { it.copy(foreignRate = ForeignRateState.Available(rate)).withPreview() }
                }
            }
        }
        viewModelScope.launch {
            val cached = currencyRepository.observeRate(code).first()
            if (!isStale(cached?.fetchedAt, clock.instant())) return@launch
            val ok = currencyRepository.refreshRate(code)
            if (!ok && cached == null) {
                // An expense already entered in this currency keeps it (and its stored rate) while unchanged.
                val keepsStoredRate = stored?.original?.currencyCode == code
                _uiState.update {
                    if (keepsStoredRate) {
                        it.copy(foreignRate = ForeignRateState.Unavailable)
                    } else {
                        // No rate at all: the trip currency can't be used, so amounts go in shekels.
                        it.copy(
                            foreignRate = ForeignRateState.Unavailable,
                            foreignEnabled = false,
                            currencyCode = ILS,
                            amountError = if (it.amountError == ExpenseFieldError.NO_RATE) null else it.amountError,
                        ).withPreview()
                    }
                }
            }
        }
    }

    private fun ExpenseFormUiState.withPreview(): ExpenseFormUiState {
        val rate = (foreignRate as? ForeignRateState.Available)?.rate
        return copy(preview = ExpenseCurrency.conversionPreview(EnteredAmount(amount, currencyCode), rate, stored))
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null) }

    fun onAmountChange(value: String) =
        _uiState.update { it.copy(amount = value, amountError = null).withPreview() }

    /** ILS or the foreign currency; the typed amount is kept and now means that currency. */
    fun onCurrencyChange(code: String) = _uiState.update { state ->
        val allowed = code == ILS || (code == state.foreignCode && state.foreignEnabled)
        if (!allowed) state else state.copy(currencyCode = code, amountError = null).withPreview()
    }

    fun onCategoryChange(category: ExpenseCategory) = _uiState.update { it.copy(category = category) }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date, dateError = null) }

    /** "Paid by". A "Not shared" expense stays not shared: its split moves to the new payer. */
    fun onPaidByChange(key: String) = _uiState.update { state ->
        if (state.people.none { it.key == key }) return@update state
        val split = if (state.isNotShared) listOf(key) else state.splitBetween
        state.copy(paidBy = key, splitBetween = split, splitError = null)
    }

    /** Adds or removes one person from "Split between". */
    fun onSplitToggle(key: String) = _uiState.update { state ->
        val order = state.order.ifEmpty { state.people.map { it.key } }
        if (key !in order) return@update state
        val split = if (key in state.splitBetween) state.splitBetween - key else state.splitBetween + key
        state.copy(splitBetween = inListOrder(split, order), splitError = null)
    }

    /** "Not shared": the split becomes the payer only. */
    fun onNotShared() = _uiState.update { it.copy(splitBetween = listOf(it.paidBy), splitError = null) }

    fun onSave() {
        val state = _uiState.value
        if (state.isBusy) return

        val rate = (state.foreignRate as? ForeignRateState.Available)?.rate
        val amount = ExpenseCurrency.resolveAmount(EnteredAmount(state.amount, state.currencyCode), rate, stored)
        val validated = state.copy(
            nameError = ExpenseValidator.validateName(state.name),
            amountError = (amount as? AmountResult.Invalid)?.error,
            dateError = ExpenseValidator.validateDate(state.date, state.tripEnd),
            splitError = ExpenseValidator.validateSplit(state.splitBetween),
        )
        _uiState.value = validated
        val invalid = validated.nameError != null || validated.dateError != null || validated.splitError != null
        if (invalid || amount !is AmountResult.Ok) return

        val ownerId = authRepository.currentUser.value?.uid ?: return
        val input = ExpenseInput(
            name = validated.name.trim(),
            amountAgorot = amount.agorot,
            category = validated.category,
            date = validated.date,
            original = amount.original,
            paidBy = validated.paidBy,
            splitBetween = validated.splitBetween,
        )
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            if (expenseId == null) {
                repository.addExpense(tripId, ownerId, input)
            } else {
                repository.updateExpense(expenseId, ownerId, input)
            }
            _uiState.update { it.copy(isSaving = false, done = true) }
        }
    }

    /** Deletes the edited expense. The confirmation dialog is shown by the screen before this is called. */
    fun onDelete() {
        val id = expenseId ?: return
        if (_uiState.value.isBusy) return
        val ownerId = authRepository.currentUser.value?.uid ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            repository.deleteExpense(id, ownerId)
            _uiState.update { it.copy(isSaving = false, done = true) }
        }
    }

    private val ExpenseFormUiState.isBusy: Boolean get() = isLoading || isSaving || done

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<ExpenseForm>()
                val container = appContainer()
                ExpenseFormViewModel(
                    tripId = route.tripId,
                    expenseId = route.expenseId,
                    repository = container.expenseRepository,
                    tripRepository = container.tripRepository,
                    currencyRepository = container.currencyRepository,
                    authRepository = container.authRepository,
                    clock = container.clock,
                    travelerRepository = container.travelerRepository,
                    memberRepository = container.tripMemberRepository,
                )
            }
        }
    }
}
