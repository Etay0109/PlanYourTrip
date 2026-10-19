package com.example.planyourtrip.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.common.SavedFormState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The "Set budget" dialog: open, typed amount, validation and saving. Kept across process death in [savedState]. */
class BudgetDialogController(
    private val tripId: Long,
    private val tripRepository: TripRepository,
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope,
    savedState: SavedStateHandle?,
    /** The loaded trip's budget, or null when there is none (or the trip isn't loaded). */
    private val currentBudget: () -> Long?,
) {
    private val form = SavedFormState<BudgetDialogState?, BudgetDialogState>(null, savedState, KEY_BUDGET_DIALOG) {
        it
    }.also { form -> form.restored?.let { restored -> form.update { restored } } }

    /** The open "Set budget" dialog, or null when closed. */
    val state: StateFlow<BudgetDialogState?> = form.flow

    /** Opens the dialog, prefilled with the current budget (empty when none). */
    fun onEdit() {
        form.update { BudgetDialogState(input = currentBudget()?.let(Money::toInput).orEmpty()) }
    }

    fun onInputChange(value: String) = form.update { it?.copy(input = value, error = null) }

    fun onDismiss() {
        form.update { null }
    }

    /** Validates (a number greater than zero), stores the budget and closes the dialog. */
    fun onSave() {
        val dialog = form.value ?: return
        val error = validateBudget(dialog.input)
        if (error != null) {
            form.update { dialog.copy(error = error) }
            return
        }
        val ownerId = authRepository.currentUser.value?.uid ?: return
        val agorot = checkNotNull(Money.parseAgorot(dialog.input))
        form.update { null }
        scope.launch { tripRepository.setBudget(tripId, ownerId, agorot) }
    }

    private companion object {
        const val KEY_BUDGET_DIALOG = "budgetDialog"
    }
}

/** Null when [input] is a valid budget: a number of shekels greater than zero. */
fun validateBudget(input: String): BudgetFieldError? {
    if (input.isBlank()) return BudgetFieldError.EMPTY
    return if (Money.parsePositiveAgorot(input) == null) BudgetFieldError.INVALID else null
}
