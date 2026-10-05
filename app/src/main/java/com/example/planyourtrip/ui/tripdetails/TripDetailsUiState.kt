package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.ui.common.TripStatus
import java.math.BigDecimal
import java.time.LocalDate

/** Tabs of the trip screen. The selected tab is pure UI state, kept in Compose. */
enum class TripTab { OVERVIEW, BUDGET, MAP, PLAN }

sealed interface TripDetailsUiState {
    /** Room has not answered yet; nothing is drawn. */
    data object Loading : TripDetailsUiState

    /** The trip does not exist (e.g. deleted) or belongs to another account. */
    data object NotFound : TripDetailsUiState

    data class Loaded(
        val trip: Trip,
        /** Header badge; null once the trip has ended. */
        val status: TripStatus?,
        /** Null when the trip has no budget yet. */
        val summary: BudgetSummary?,
        val categoryTotals: List<CategoryTotal>,
        /** Newest first. */
        val expenses: List<Expense>,
    ) : TripDetailsUiState
}

/** Error codes of the budget dialog, mapped to string resources in the UI. */
enum class BudgetFieldError { EMPTY, INVALID }

/** The "Set budget" dialog while it is open. */
data class BudgetDialogState(
    val input: String,
    val error: BudgetFieldError? = null,
)

/** The currency card on the Overview tab. */
sealed interface CurrencyCardState {
    /** The trip has no currency yet: message + "Choose currency". */
    data object NoCurrency : CurrencyCardState

    /** The trip currency is the shekel: no rate and no converter, just a one-line message. */
    data object Shekel : CurrencyCardState

    /** No cached rate yet and it is being fetched. */
    data class Loading(val code: String) : CurrencyCardState

    /** No cached rate and the fetch failed (e.g. offline): message + "Try again". */
    data class Unavailable(val code: String) : CurrencyCardState

    /**
     * 1 [code] = [ilsPerUnit] shekels, dated [date] by the service (possibly cached). The converter's text lives
     * in the card (Compose state), so typing never round-trips through the ViewModel.
     */
    data class Rate(
        val code: String,
        val ilsPerUnit: BigDecimal,
        val date: LocalDate,
    ) : CurrencyCardState
}
