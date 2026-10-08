package com.example.planyourtrip.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.planyourtrip.data.sync.ChildRef
import com.example.planyourtrip.data.sync.DeletionEntity
import com.example.planyourtrip.data.sync.SyncKind
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
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
        "UPDATE expenses SET name = :name, amount_agorot = :amountAgorot, category = :category, date = :date, " +
            "original_amount = :originalAmount, original_currency = :originalCurrency, " +
            "exchange_rate = :exchangeRate, rate_date = :rateDate, paid_by = :paidBy, split_between = :splitBetween, " +
            "updated_at = :updatedAt, pending_sync = 1 " +
            "WHERE id = :id AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun update(
        id: Long,
        ownerId: String,
        name: String,
        amountAgorot: Long,
        category: ExpenseCategory,
        date: LocalDate,
        originalAmount: BigDecimal?,
        originalCurrency: String?,
        exchangeRate: BigDecimal?,
        rateDate: LocalDate?,
        paidBy: String,
        splitBetween: List<String>,
        updatedAt: Long,
    )

    @Query("DELETE FROM expenses WHERE id = :id AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)")
    suspend fun delete(id: Long, ownerId: String)

    @Query(
        "SELECT e.uuid AS uuid, t.uuid AS trip_uuid FROM expenses e JOIN trips t ON t.id = e.trip_id " +
            "WHERE e.id = :id AND t.owner_id = :ownerId"
    )
    suspend fun ref(id: Long, ownerId: String): ChildRef?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletion(deletion: DeletionEntity)

    /** Deletes the expense and records the delete, so it reaches the cloud and the account's other devices. */
    @Transaction
    suspend fun deleteAndRecord(id: Long, ownerId: String, deletedAt: Long) {
        val ref = ref(id, ownerId) ?: return
        insertDeletion(DeletionEntity(ref.uuid, ownerId, SyncKind.EXPENSE, ref.tripUuid, deletedAt))
        delete(id, ownerId)
    }
}
