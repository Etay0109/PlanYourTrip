package com.example.planyourtrip.data.trip

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val tripDao: TripDao,
) : ExpenseRepository {

    override fun observeExpenses(tripId: Long, ownerId: String): Flow<List<Expense>> =
        expenseDao.observeForTrip(tripId, ownerId).map { list -> list.map { it.toExpense() } }

    override fun observeExpense(id: Long, ownerId: String): Flow<Expense?> =
        expenseDao.observeById(id, ownerId).map { it?.toExpense() }

    override suspend fun addExpense(
        tripId: Long,
        ownerId: String,
        name: String,
        amountAgorot: Long,
        category: ExpenseCategory,
        date: LocalDate,
    ): Long? {
        // Never attach an expense to another account's trip.
        if (!tripDao.exists(tripId, ownerId)) return null
        return expenseDao.insert(
            ExpenseEntity(
                tripId = tripId,
                name = name.trim(),
                amountAgorot = amountAgorot,
                category = category,
                date = date,
            )
        )
    }

    override suspend fun updateExpense(
        id: Long,
        ownerId: String,
        name: String,
        amountAgorot: Long,
        category: ExpenseCategory,
        date: LocalDate,
    ) = expenseDao.update(id, ownerId, name.trim(), amountAgorot, category, date)

    override suspend fun deleteExpense(id: Long, ownerId: String) = expenseDao.delete(id, ownerId)
}
