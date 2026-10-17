package com.example.planyourtrip.data.trip

import com.example.planyourtrip.data.sync.LocalChangeListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

/** Every change is stamped with [clock], marked pending (deletes are recorded) and reported to [changes]. */
class RoomExpenseRepository(
    private val expenseDao: ExpenseDao,
    private val tripDao: TripDao,
    private val clock: Clock = Clock.systemUTC(),
    private val changes: LocalChangeListener = LocalChangeListener.None,
) : ExpenseRepository {

    override fun observeExpenses(tripId: Long, ownerId: String): Flow<List<Expense>> =
        expenseDao.observeForTrip(tripId, ownerId).map { list -> list.map { it.toExpense() } }

    override fun observeExpense(id: Long, ownerId: String): Flow<Expense?> =
        expenseDao.observeById(id, ownerId).map { it?.toExpense() }

    override suspend fun addExpense(tripId: Long, ownerId: String, input: ExpenseInput): Long? {
        // Never attach an expense to another account's trip.
        if (!tripDao.exists(tripId, ownerId)) return null
        val original = input.original
        return expenseDao.insert(
            ExpenseEntity(
                tripId = tripId,
                name = input.name.trim().limitTo(TextLimits.EXPENSE_NAME),
                amountAgorot = input.amountAgorot,
                category = input.category,
                date = input.date,
                originalAmount = original?.amount,
                originalCurrency = original?.currencyCode,
                exchangeRate = original?.ilsPerUnit,
                rateDate = original?.rateDate,
                paidBy = input.paidBy,
                splitBetween = cleanSplit(input.paidBy, input.splitBetween),
                updatedAt = clock.millis(),
            )
        ).also { changes.onLocalChange() }
    }

    override suspend fun updateExpense(id: Long, ownerId: String, input: ExpenseInput) {
        val original = input.original
        expenseDao.update(
            id, ownerId, input.name.trim().limitTo(TextLimits.EXPENSE_NAME), input.amountAgorot, input.category,
            input.date,
            original?.amount, original?.currencyCode, original?.ilsPerUnit, original?.rateDate,
            input.paidBy, cleanSplit(input.paidBy, input.splitBetween), clock.millis(),
        )
        changes.onLocalChange()
    }

    override suspend fun deleteExpense(id: Long, ownerId: String) {
        expenseDao.deleteAndRecord(id, ownerId, clock.millis())
        changes.onLocalChange()
    }

    /** No duplicates; an empty split means "not shared" (the payer only). */
    private fun cleanSplit(paidBy: String, splitBetween: List<String>): List<String> =
        splitBetween.distinct().ifEmpty { listOf(paidBy) }
}
