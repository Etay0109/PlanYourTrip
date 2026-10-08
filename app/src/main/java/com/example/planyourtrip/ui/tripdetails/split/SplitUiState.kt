package com.example.planyourtrip.ui.tripdetails.split

import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.common.split.TravelerNameError
import com.example.planyourtrip.ui.common.split.Transfer
import com.example.planyourtrip.ui.common.split.personOf

/** The Budget tab's Travelers section, "Who owes whom" card and settle-up sheet of one trip. */
sealed interface SplitUiState {
    /** Room has not answered yet. */
    data object Loading : SplitUiState

    /** Not the signed-in user's trip. */
    data object NotFound : SplitUiState

    data class Loaded(
        /** Besides "You", in list order. */
        val travelers: List<Traveler>,
        /** Open debts, smallest number of transfers; rows involving "You" first. Empty = all settled up. */
        val transfers: List<Transfer>,
        /** "Based on N shared expenses". */
        val sharedExpenseCount: Int,
        /** Recorded payments, newest first ("History"). */
        val settlements: List<Settlement>,
    ) : SplitUiState {
        /** "You" and the travelers, in list order. */
        val people: List<Person> get() = listOf(Person.You) + travelers.map { personOf(it.uuid, travelers) }

        /** "Who owes whom" (and "Paid by" / "Split between" elsewhere) only exist with more than one traveler. */
        val hasOthers: Boolean get() = travelers.isNotEmpty()

        fun person(key: String): Person = personOf(key, travelers)
    }
}

/** The add / rename-or-remove traveler dialog. */
data class TravelerDialogState(
    /** Null = adding a new traveler. */
    val editing: Traveler? = null,
    val name: String = "",
    val error: TravelerNameError? = null,
    /** The edited traveler paid for or shares an expense, or appears in a settlement: "Remove" is disabled. */
    val removeBlocked: Boolean = false,
    val isSaving: Boolean = false,
)

enum class SettlePage { TRANSFERS, HISTORY }

/** Problems with the settle-up amount. */
enum class SettleAmountError { EMPTY, INVALID, TOO_MUCH }

/** The "Settle up" bottom sheet. */
data class SettleSheetState(
    val page: SettlePage = SettlePage.TRANSFERS,
    /** The open transfer being paid; null until one is picked. */
    val selected: Transfer? = null,
    /** The amount in shekels as typed; starts at the full amount of [selected] (less = a partial payment). */
    val amount: String = "",
    val error: SettleAmountError? = null,
    val isSaving: Boolean = false,
)
