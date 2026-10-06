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

/** Spending per category, largest first; categories without spending are left out. */
fun categoryTotals(expenses: List<Expense>): List<CategoryTotal> {
    val total = expenses.sumOf { it.amountAgorot }
    if (total <= 0) return emptyList()
    return expenses
        .groupBy { it.category }
        .map { (category, items) -> category to items.sumOf { it.amountAgorot } }
        .filter { (_, amount) -> amount > 0 }
        // Ties keep the enum order, so the chart never reshuffles between recompositions.
        .sortedWith(compareByDescending<Pair<ExpenseCategory, Long>> { it.second }.thenBy { it.first.ordinal })
        .map { (category, amount) -> CategoryTotal(category, amount, amount * 100.0 / total) }
}
