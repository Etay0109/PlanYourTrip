package com.example.planyourtrip.ui.common

import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory

// Pure spending-per-category math shared by the Budget tab and the shared PDF (unit-tested).

data class CategoryTotal(
    val category: ExpenseCategory,
    val amountAgorot: Long,
    /** Share of all spending, e.g. 40.0. */
    val percent: Double,
)

/**
 * Spending per category, largest first. [amountOf] is the amount that counts for each expense (my share on the
 * Budget tab and in the PDF). Categories without spending are left out, unless [includeZeroCategories]: then the
 * categories that have expenses but nothing counted (only expenses I'm not part of) follow as ₪0, 0%, in enum order.
 */
fun categoryTotals(
    expenses: List<Expense>,
    amountOf: (Expense) -> Long = { it.amountAgorot },
    includeZeroCategories: Boolean = false,
): List<CategoryTotal> {
    val sums = expenses
        .groupBy { it.category }
        .map { (category, items) -> category to items.sumOf(amountOf) }
    val total = sums.sumOf { it.second }
    val spent = if (total <= 0) {
        emptyList()
    } else {
        sums
            .filter { (_, amount) -> amount > 0 }
            // Ties keep the enum order, so the chart never reshuffles between recompositions.
            .sortedWith(compareByDescending<Pair<ExpenseCategory, Long>> { it.second }.thenBy { it.first.ordinal })
            .map { (category, amount) -> CategoryTotal(category, amount, amount * 100.0 / total) }
    }
    if (!includeZeroCategories) return spent
    val zero = sums
        .filter { (_, amount) -> amount <= 0 }
        .sortedBy { it.first.ordinal }
        .map { (category, _) -> CategoryTotal(category, 0, 0.0) }
    return spent + zero
}
