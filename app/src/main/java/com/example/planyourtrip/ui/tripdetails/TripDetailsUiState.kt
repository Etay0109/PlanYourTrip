package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.ui.common.CategoryTotal
import com.example.planyourtrip.ui.common.TripStatus
import com.example.planyourtrip.ui.tripdetails.components.ExpenseSplitInfo
import com.example.planyourtrip.ui.tripdetails.members.MembersState
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDate

/** Tabs of the trip screen. The selected tab is pure UI state, kept in Compose. */
enum class TripTab { OVERVIEW, BUDGET, PREP, PLAN }

sealed interface TripDetailsUiState {
    /** Room has not answered yet; nothing is drawn. */
    data object Loading : TripDetailsUiState

    /** The trip does not exist (e.g. deleted) or belongs to another account. */
    data object NotFound : TripDetailsUiState

    data class Loaded(
        val trip: Trip,
        /** Header badge; null once the trip has ended. */
        val status: TripStatus?,
        /** Null when the trip has no budget yet. Counts only my share of each expense. */
        val summary: BudgetSummary?,
        val categoryTotals: List<CategoryTotal>,
        /** Newest first. */
        val expenses: List<Expense>,
        /** The trip's travelers besides "You", in list order (empty = only me: no split shown anywhere). */
        val travelers: List<Traveler> = emptyList(),
        /** My share of each expense, by expense id (what the budget and the chart count). */
        val myShares: Map<Long, Long> = emptyMap(),
        /** Payer, split and my share of each expense row, by expense id; empty when the trip has only me. */
        val expenseSplits: Map<Long, ExpenseSplitInfo> = emptyMap(),
        /** Plan tab, Daily plan card and the Overview day section. */
        val itinerary: ItineraryState,
        /** The Overview's "Traveling together" card; null only in previews and tests that don't need it. */
        val members: MembersState? = null,
        /** Today's year: the header's dates leave it out for a trip in this year ("19–24 Oct"); null = always shown. */
        val currentYear: Int? = null,
    ) : TripDetailsUiState
}

/** Everything the itinerary UI needs, built by the pure [itineraryState]. */
data class ItineraryState(
    /** Every trip day, first to last (one day chip each). */
    val days: List<LocalDate>,
    /** False while the trip has no activities at all (Daily plan empty state, Overview day section hidden). */
    val hasActivities: Boolean,
    val progress: PlanProgress,
    /** The day selected on the Plan tab. */
    val plan: PlanDayState,
    /** The day shown in the Overview day section. */
    val overviewDay: OverviewDayState,
    /** Activities whose date is outside the (edited) trip dates, shown at the end of the Plan tab. */
    val outside: List<TripActivity>,
)

data class PlanDayState(
    val day: LocalDate,
    /** 1-based ("Day 1"). */
    val dayNumber: Int,
    /** Timeline order: by start time, activities without a time last. */
    val activities: List<TripActivity>,
    /** "~₪210 planned"; null when no activity of the day has a cost. */
    val costAgorot: Long?,
)

data class OverviewDayState(
    val day: LocalDate,
    val title: DayTitle,
    /** At most [OVERVIEW_DAY_MAX_ACTIVITIES], in timeline order. */
    val activities: List<TripActivity>,
    /** "+N more"; 0 hides it. */
    val moreCount: Int,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

/** Error codes of the budget dialog, mapped to string resources in the UI. */
enum class BudgetFieldError { EMPTY, INVALID }

/** The "Set budget" dialog while it is open. */
/** The budget dialog: the typed amount as the ViewModel last got it (Serializable: it comes back after process death). */
data class BudgetDialogState(
    val input: String,
    val error: BudgetFieldError? = null,
) : Serializable

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
