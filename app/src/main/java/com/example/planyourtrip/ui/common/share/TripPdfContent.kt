package com.example.planyourtrip.ui.common.share

import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.ui.common.CategoryTotal
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.activitiesOn
import com.example.planyourtrip.ui.common.categoryTotals
import com.example.planyourtrip.ui.common.split.myShare
import com.example.planyourtrip.ui.common.split.travelerOrder
import com.example.planyourtrip.ui.common.dayNumber
import com.example.planyourtrip.ui.common.tripDays
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

// What the shared trip PDF says, as plain data (no Android, unit-tested in TripPdfContentTest). The drawing code
// (AndroidTripPdfExporter) only lays this out. The model has no room for the user's email, the list of
// expenses or activity costs, so they can never end up in a shared file.

data class TripPdfContent(
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val lengthDays: Int,
    /** Null when costs are left out or the trip has no expenses: no cost section at all. */
    val costs: CostSummary?,
    /** One entry per trip day; null when no activity falls inside the trip (one "nothing planned" line). */
    val days: List<PdfDay>?,
)

data class CostSummary(
    val spentAgorot: Long,
    /** Null when the trip has no budget: only the total spent is shown. */
    val budgetAgorot: Long?,
    /** Null exactly when [budgetAgorot] is null. */
    val difference: BudgetDifference?,
    /** Largest first, only categories with spending. */
    val categories: List<CategoryTotal>,
)

sealed interface BudgetDifference {
    /** "₪450 under budget" */
    data class Under(val agorot: Long) : BudgetDifference

    /** "₪200 over budget" */
    data class Over(val agorot: Long) : BudgetDifference

    data object OnBudget : BudgetDifference
}

/** "Day 1 · Thu 12 Nov"; an empty [activities] list is shown as "Nothing planned". */
data class PdfDay(
    val number: Int,
    val date: LocalDate,
    val activities: List<PdfActivity>,
)

data class PdfActivity(
    val time: LocalTime?,
    val title: String,
    val type: ActivityType,
    val location: String?,
    val note: String?,
)

/**
 * Builds the PDF content. With [includeCosts] off there is no money information at all; with it on, the cost section
 * counts only my share of each expense (like the Budget tab), split with [travelers]. Activities outside the
 * trip dates are left out; each day lists its activities in timeline order (untimed last).
 */
fun buildTripPdfContent(
    trip: Trip,
    expenses: List<Expense>,
    activities: List<TripActivity>,
    includeCosts: Boolean,
    travelers: List<Traveler> = emptyList(),
): TripPdfContent {
    val days = tripDays(trip.startDate, trip.endDate).map { day ->
        PdfDay(
            number = dayNumber(day, trip.startDate),
            date = day,
            activities = activitiesOn(day, activities).map { it.toPdfActivity() },
        )
    }
    return TripPdfContent(
        destination = trip.destination,
        startDate = trip.startDate,
        endDate = trip.endDate,
        lengthDays = TripFormatting.lengthInDays(trip.startDate, trip.endDate),
        costs = if (includeCosts) costSummary(trip.budgetAgorot, expenses, travelerOrder(travelers)) else null,
        days = days.takeIf { list -> list.any { it.activities.isNotEmpty() } },
    )
}

private fun costSummary(budgetAgorot: Long?, expenses: List<Expense>, order: List<String>): CostSummary? {
    if (expenses.isEmpty()) return null
    val myShares = expenses.associate { it.id to it.myShare(order) }
    val spent = myShares.values.sum()
    val budget = budgetAgorot?.takeIf { it > 0 }
    val difference = budget?.let {
        val left = it - spent
        when {
            left > 0 -> BudgetDifference.Under(left)
            left < 0 -> BudgetDifference.Over(-left)
            else -> BudgetDifference.OnBudget
        }
    }
    return CostSummary(spent, budget, difference, categoryTotals(expenses, amountOf = { myShares.getValue(it.id) }))
}

private fun TripActivity.toPdfActivity() = PdfActivity(
    time = startTime,
    title = title,
    type = type,
    location = location?.trim()?.takeIf { it.isNotEmpty() },
    note = note?.trim()?.takeIf { it.isNotEmpty() },
)

/** Longest destination part of a file name, so the name stays readable and within file-system limits. */
private const val MAX_NAME_CHARS = 60

/**
 * Anything that isn't a letter or digit. Combining marks (accents, Hebrew vowel points) stay only after a letter
 * or digit, so an emoji's variation selector (U+FE0F, also a mark) goes with the emoji.
 */
private val NOT_LETTER_OR_DIGIT = Regex("""(?:[^\p{L}\p{M}\p{N}]|(?<![\p{L}\p{M}\p{N}])\p{M})+""")

/**
 * "Rome-Italy-12-17-Nov-2026.pdf": the destination and the dates (always English month names), every run of
 * characters that aren't letters or digits (any script, so Hebrew stays) replaced by "-". A destination with
 * no letters or digits at all becomes "Trip".
 */
fun pdfFileName(destination: String, start: LocalDate, end: LocalDate): String {
    val name = clean(destination).take(MAX_NAME_CHARS).trimEnd('-').ifEmpty { "Trip" }
    val dates = clean(TripFormatting.dateRange(start, end, Locale.ENGLISH))
    return "$name-$dates.pdf"
}

private fun clean(text: String): String = text.replace(NOT_LETTER_OR_DIGIT, "-").trim('-')
