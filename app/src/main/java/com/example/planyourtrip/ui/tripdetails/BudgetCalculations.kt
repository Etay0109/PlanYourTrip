package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.ui.common.TripFormatting
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Pure budget math (no Android dependencies, unit-tested). All money is whole agorot.

/** Spending above this share of the budget turns the progress bar orange. */
const val BUDGET_WARNING_PERCENT = 80.0

/** "About ₪X per day for N days". [amountAgorot] is rounded down to whole shekels (when at least ₪1). */
data class PerDay(val amountAgorot: Long, val days: Int)

data class BudgetSummary(
    val totalAgorot: Long,
    val spentAgorot: Long,
    /** Negative when spending is over the budget. */
    val leftAgorot: Long,
    /** Share of the budget spent, e.g. 57.5. Can be above 100. */
    val spentPercent: Double,
    /** Progress bar fill, clamped to 0..1. */
    val progress: Float,
    /** More than [BUDGET_WARNING_PERCENT] spent. */
    val isWarning: Boolean,
    val isOverBudget: Boolean,
    /** Null after the trip, or when nothing is left to spend. */
    val perDay: PerDay?,
)

/**
 * Null when the trip has no budget yet. [spentAgorot] is what I spent: my share of every expense (see `myTotal`),
 * so expenses paid for others never count.
 */
fun budgetSummary(
    budgetAgorot: Long?,
    spentAgorot: Long,
    start: LocalDate,
    end: LocalDate,
    today: LocalDate,
): BudgetSummary? {
    if (budgetAgorot == null || budgetAgorot <= 0) return null
    val spent = spentAgorot
    val left = budgetAgorot - spent
    val percent = spent * 100.0 / budgetAgorot
    val days = remainingBudgetDays(start, end, today)
    return BudgetSummary(
        totalAgorot = budgetAgorot,
        spentAgorot = spent,
        leftAgorot = left,
        spentPercent = percent,
        progress = (spent.toDouble() / budgetAgorot).coerceIn(0.0, 1.0).toFloat(),
        isWarning = percent > BUDGET_WARNING_PERCENT,
        isOverBudget = left < 0,
        perDay = if (days != null && left > 0) PerDay(perDayAmount(left, days), days) else null,
    )
}

/** Even share of [left] per day, rounded down to whole shekels ("About ₪425"), or exact agorot below ₪1. */
private fun perDayAmount(left: Long, days: Int): Long {
    val share = left / days
    return if (share >= 100) share / 100 * 100 else share
}

/**
 * Days the remaining budget has to cover: the whole trip (inclusive) before it starts, the days left
 * including today during it, and null after it ends.
 */
fun remainingBudgetDays(start: LocalDate, end: LocalDate, today: LocalDate): Int? = when {
    today.isAfter(end) -> null
    today.isBefore(start) -> TripFormatting.lengthInDays(start, end)
    else -> ChronoUnit.DAYS.between(today, end).toInt() + 1
}
