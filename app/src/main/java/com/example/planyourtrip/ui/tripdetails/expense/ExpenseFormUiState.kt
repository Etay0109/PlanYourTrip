package com.example.planyourtrip.ui.tripdetails.expense

import com.example.planyourtrip.data.currency.ExchangeRate
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.data.trip.ExpenseCategory
import java.time.LocalDate

data class ExpenseFormUiState(
    /** True when changing an existing expense (shows "Edit expense" and the delete action). */
    val isEditing: Boolean = false,
    /** True until the trip's currency (and, when editing, the expense) is loaded; the fields stay hidden. */
    val isLoading: Boolean = true,
    val name: String = "",
    /** Amount as typed in [currencyCode], e.g. "120.50" or "1500". Converted on save. */
    val amount: String = "",
    /** ILS or [foreignCode]. */
    val currencyCode: String = ILS,
    /**
     * The other currency offered next to ILS: the trip currency, or the edited expense's original currency.
     * Null = only ILS (the trip has no currency, or it is ILS).
     */
    val foreignCode: String? = null,
    /** The rate of [foreignCode]; null when there is none. */
    val foreignRate: ForeignRateState? = null,
    /**
     * Whether [foreignCode] can be selected: it has a rate, or the edited expense was entered in it (its stored
     * rate still applies while the amount is unchanged).
     */
    val foreignEnabled: Boolean = true,
    /** "≈ ₪40.20 · 1 EUR = ₪4.02 · rate of 2 Oct"; null when not a valid foreign amount. */
    val preview: ConversionPreview? = null,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val date: LocalDate,
    /** The trip's last day: the latest date the picker offers. Null until the trip is loaded. */
    val tripEnd: LocalDate? = null,
    val nameError: ExpenseFieldError? = null,
    val amountError: ExpenseFieldError? = null,
    /** [ExpenseFieldError.DATE_AFTER_TRIP]; also shown when an edited expense is already dated after the trip. */
    val dateError: ExpenseFieldError? = null,
    val isSaving: Boolean = false,
    /** Set once the expense is saved or deleted; the screen then navigates back. */
    val done: Boolean = false,
)

/** The rate behind the foreign currency option. */
sealed interface ForeignRateState {
    /** No cached rate yet; it is being fetched. */
    data object Loading : ForeignRateState

    /** No cached rate and it could not be fetched: the option is disabled and amounts go in ILS. */
    data object Unavailable : ForeignRateState

    /** The latest cached rate (possibly older than 12 hours when offline). */
    data class Available(val rate: ExchangeRate) : ForeignRateState
}
