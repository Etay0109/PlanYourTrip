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
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ExpenseRepository
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.navigation.ExpenseForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/** New expense of [tripId] when [expenseId] is null; otherwise edits (or deletes) that expense. */
class ExpenseFormViewModel(
    private val tripId: Long,
    private val expenseId: Long?,
    private val repository: ExpenseRepository,
    private val authRepository: AuthRepository,
    clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ExpenseFormUiState(
            isEditing = expenseId != null,
            isLoading = expenseId != null,
            date = LocalDate.now(clock),
        )
    )
    val uiState: StateFlow<ExpenseFormUiState> = _uiState.asStateFlow()

    init {
        if (expenseId != null) loadExpense(expenseId)
    }

    /** Fills the fields once with the expense's current values (only the owner's expenses). */
    private fun loadExpense(id: Long) {
        val ownerId = authRepository.currentUser.value?.uid ?: return
        viewModelScope.launch {
            val expense = repository.observeExpense(id, ownerId).first()
            _uiState.update {
                if (expense == null) it.copy(isLoading = false)
                else it.copy(
                    isLoading = false,
                    name = expense.name,
                    amount = Money.toInput(expense.amountAgorot),
                    category = expense.category,
                    date = expense.date,
                )
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, nameError = null) }

    fun onAmountChange(value: String) = _uiState.update { it.copy(amount = value, amountError = null) }

    fun onCategoryChange(category: ExpenseCategory) = _uiState.update { it.copy(category = category) }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }

    fun onSave() {
        val state = _uiState.value
        if (state.isBusy) return

        val validated = state.copy(
            nameError = ExpenseValidator.validateName(state.name),
            amountError = ExpenseValidator.validateAmount(state.amount),
        )
        _uiState.value = validated
        if (validated.nameError != null || validated.amountError != null) return

        val ownerId = authRepository.currentUser.value?.uid ?: return
        val name = validated.name.trim()
        val agorot = checkNotNull(Money.parseAgorot(validated.amount))
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            if (expenseId == null) {
                repository.addExpense(tripId, ownerId, name, agorot, validated.category, validated.date)
            } else {
                repository.updateExpense(expenseId, ownerId, name, agorot, validated.category, validated.date)
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
                    authRepository = container.authRepository,
                    clock = container.clock,
                )
            }
        }
    }
}
