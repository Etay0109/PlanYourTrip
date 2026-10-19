package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TripMember
import com.example.planyourtrip.ui.common.CategoryTotal
import com.example.planyourtrip.ui.common.categoryTotals
import com.example.planyourtrip.ui.common.split.inListOrder
import com.example.planyourtrip.ui.common.split.myShare
import com.example.planyourtrip.ui.common.split.personOf
import com.example.planyourtrip.ui.common.split.travelerOrder
import com.example.planyourtrip.ui.tripdetails.components.ExpenseSplitInfo
import java.time.LocalDate

/** The Budget tab's numbers: everything that depends on the expenses and how they are split. */
data class BudgetPart(
    /** Null when the trip has no budget yet. Counts only my share of each expense. */
    val summary: BudgetSummary?,
    val categoryTotals: List<CategoryTotal>,
    /** My share of each expense, by expense id. */
    val myShares: Map<Long, Long>,
    /** Payer, split and my share of each expense row; empty when the trip has only me. */
    val expenseSplits: Map<Long, ExpenseSplitInfo>,
)

/** The budget counts only my share of each expense. */
fun budgetPart(data: TripData, today: LocalDate): BudgetPart {
    val trip = data.trip
    val order = travelerOrder(data.uid, trip.memberIds, data.travelers)
    val myShares = data.expenses.associate { it.id to it.myShare(order, data.uid) }
    return BudgetPart(
        summary = budgetSummary(trip.budgetAgorot, myShares.values.sum(), trip.startDate, trip.endDate, today),
        categoryTotals = categoryTotals(
            data.expenses, amountOf = { myShares.getValue(it.id) }, includeZeroCategories = true,
        ),
        myShares = myShares,
        expenseSplits = expenseSplits(
            data.expenses, data.travelers, myShares, data.uid, order, trip.memberIds, data.members,
        ),
    )
}

/**
 * Keeps the last [BudgetPart], so only new trip data or a new day computes it again; choosing a day on the Plan tab
 * or in the Overview reuses it. Used by one collector at a time.
 */
class BudgetCache {
    private var lastData: TripData? = null
    private var lastToday: LocalDate? = null
    private var last: BudgetPart? = null

    fun of(data: TripData, today: LocalDate): BudgetPart {
        last?.let { if (data === lastData && today == lastToday) return it }
        return budgetPart(data, today).also {
            lastData = data
            lastToday = today
            last = it
        }
    }
}

/**
 * What each expense row shows with other travelers: who paid, who shares it (in list [order]) and my ([me]) share
 * (from [myShares]). Empty when the trip has only me, so the rows show no split line.
 */
internal fun expenseSplits(
    expenses: List<Expense>,
    travelers: List<Traveler>,
    myShares: Map<Long, Long>,
    me: String,
    order: List<String>,
    memberIds: List<String> = emptyList(),
    members: List<TripMember> = emptyList(),
): Map<Long, ExpenseSplitInfo> {
    if (order.none { it != me }) return emptyMap()
    fun person(key: String) = personOf(key, me, travelers, memberIds, members)
    return expenses.associate { expense ->
        expense.id to ExpenseSplitInfo(
            payer = person(expense.paidBy),
            splitWith = inListOrder(expense.splitBetween, order).map(::person),
            myShareAgorot = myShares[expense.id] ?: 0L,
        )
    }
}
