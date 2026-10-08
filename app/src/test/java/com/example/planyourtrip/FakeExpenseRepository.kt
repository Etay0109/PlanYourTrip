package com.example.planyourtrip

import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseInput
import com.example.planyourtrip.data.trip.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * In-memory [ExpenseRepository] for unit tests. Mirrors RoomExpenseRepository: expenses are visible and
 * changeable only by the owner of their trip (looked up in [trips]), and lists are newest first.
 */
class FakeExpenseRepository(
    private val trips: FakeTripRepository,
    initial: List<Expense> = emptyList(),
) : ExpenseRepository {
    val expenses = MutableStateFlow(initial)

    private fun owns(ownerId: String, tripId: Long) =
        trips.trips.value.any { (owner, trip) -> owner == ownerId && trip.id == tripId }

    override fun observeExpenses(tripId: Long, ownerId: String): Flow<List<Expense>> =
        combine(expenses, trips.trips) { list, _ ->
            if (!owns(ownerId, tripId)) emptyList()
            else list.filter { it.tripId == tripId }
                .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
        }

    override fun observeExpense(id: Long, ownerId: String): Flow<Expense?> =
        combine(expenses, trips.trips) { list, _ -> list.find { it.id == id && owns(ownerId, it.tripId) } }

    override suspend fun addExpense(tripId: Long, ownerId: String, input: ExpenseInput): Long? {
        if (!owns(ownerId, tripId)) return null
        val id = (expenses.value.maxOfOrNull { it.id } ?: 0) + 1
        val split = input.splitBetween.distinct().ifEmpty { listOf(input.paidBy) }
        expenses.update {
            it + with(input) {
                Expense(id, tripId, name.trim(), amountAgorot, category, date, original, paidBy, split)
            }
        }
        return id
    }

    override suspend fun updateExpense(id: Long, ownerId: String, input: ExpenseInput) = expenses.update { list ->
        list.map {
            if (it.id == id && owns(ownerId, it.tripId)) {
                it.copy(
                    name = input.name.trim(), amountAgorot = input.amountAgorot, category = input.category,
                    date = input.date, original = input.original, paidBy = input.paidBy,
                    splitBetween = input.splitBetween.distinct().ifEmpty { listOf(input.paidBy) },
                )
            } else it
        }
    }

    override suspend fun deleteExpense(id: Long, ownerId: String) =
        expenses.update { list -> list.filterNot { it.id == id && owns(ownerId, it.tripId) } }
}
