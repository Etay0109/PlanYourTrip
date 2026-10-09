package com.example.planyourtrip.data.trip

import kotlinx.coroutines.flow.Flow

/**
 * Expenses of the owner's trips; callers pass the signed-in user's uid as [ownerId]. Amounts are in agorot;
 * [ForeignAmount] (null = entered in shekels) records the original amount and the rate used.
 * [ExpenseInput.paidBy] and [ExpenseInput.splitBetween] are traveler keys (a member uid or a traveler uuid); an
 * empty split is stored as the payer only.
 */
interface ExpenseRepository {
    /** The trip's expenses, newest first (by date, then by id). Empty if the trip is not the owner's. */
    fun observeExpenses(tripId: Long, ownerId: String): Flow<List<Expense>>

    /** One expense, or null if it does not exist or belongs to someone else. */
    fun observeExpense(id: Long, ownerId: String): Flow<Expense?>

    /** Adds an expense to one of the owner's trips. Returns the new id, or null if the trip is not the owner's. */
    suspend fun addExpense(tripId: Long, ownerId: String, input: ExpenseInput): Long?

    suspend fun updateExpense(id: Long, ownerId: String, input: ExpenseInput)

    suspend fun deleteExpense(id: Long, ownerId: String)
}
