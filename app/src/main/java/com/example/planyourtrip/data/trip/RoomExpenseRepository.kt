package com.example.planyourtrip.data.trip

import com.example.planyourtrip.data.sync.LocalChangeListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

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

    override suspend fun addExpense(
        tripId: Long,
        ownerId: String,
        name: String,
        amountAgorot: Long,
        category: ExpenseCategory,
        date: LocalDate,
        original: ForeignAmount?,
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
                originalAmount = original?.amount,
                originalCurrency = original?.currencyCode,
                exchangeRate = original?.ilsPerUnit,
                rateDate = original?.rateDate,
                updatedAt = clock.millis(),
            )
        ).also { changes.onLocalChange() }
    }

    override suspend fun updateExpense(
        id: Long,
        ownerId: String,
        name: String,
        amountAgorot: Long,
        category: ExpenseCategory,
        date: LocalDate,
        original: ForeignAmount?,
    ) {
        expenseDao.update(
            id, ownerId, name.trim(), amountAgorot, category, date,
            original?.amount, original?.currencyCode, original?.ilsPerUnit, original?.rateDate,
            clock.millis(),
        )
        changes.onLocalChange()
    }

    override suspend fun deleteExpense(id: Long, ownerId: String) {
        expenseDao.deleteAndRecord(id, ownerId, clock.millis())
        changes.onLocalChange()
    }
}
