package com.example.planyourtrip.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Every query is scoped to the owner of the expense's trip, so an account never sees or
 * changes another account's expenses.
 */
@Dao
interface ExpenseDao {
    @Query(
        "SELECT e.* FROM expenses e JOIN trips t ON t.id = e.trip_id " +
            "WHERE e.trip_id = :tripId AND t.owner_id = :ownerId ORDER BY e.date DESC, e.id DESC"
    )
    fun observeForTrip(tripId: Long, ownerId: String): Flow<List<ExpenseEntity>>

    @Query(
        "SELECT e.* FROM expenses e JOIN trips t ON t.id = e.trip_id " +
            "WHERE e.id = :id AND t.owner_id = :ownerId"
    )
    fun observeById(id: Long, ownerId: String): Flow<ExpenseEntity?>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query(
        "UPDATE expenses SET name = :name, amount_agorot = :amountAgorot, category = :category, date = :date " +
            "WHERE id = :id AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun update(
        id: Long,
        ownerId: String,
        name: String,
        amountAgorot: Long,
        category: ExpenseCategory,
        date: LocalDate,
    )

    @Query("DELETE FROM expenses WHERE id = :id AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)")
    suspend fun delete(id: Long, ownerId: String)
}
