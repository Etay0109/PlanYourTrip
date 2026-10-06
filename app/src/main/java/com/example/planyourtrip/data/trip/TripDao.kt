package com.example.planyourtrip.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Every query is scoped to one owner, so an account never sees another account's trips. Every change sets
 * `updated_at` and `pending_sync` so the sync layer uploads it.
 */
@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE owner_id = :ownerId ORDER BY start_date ASC, id ASC")
    fun observeAll(ownerId: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id AND owner_id = :ownerId")
    fun observeById(id: Long, ownerId: String): Flow<TripEntity?>

    @Query("SELECT EXISTS(SELECT 1 FROM trips WHERE id = :id AND owner_id = :ownerId)")
    suspend fun exists(id: Long, ownerId: String): Boolean

    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Query(
        "UPDATE trips SET destination = :destination, start_date = :startDate, end_date = :endDate, " +
            "currency_code = :currencyCode, updated_at = :updatedAt, pending_sync = 1 " +
            "WHERE id = :id AND owner_id = :ownerId"
    )
    suspend fun update(
        id: Long,
        ownerId: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        currencyCode: String?,
        updatedAt: Long,
    )

    @Query(
        "UPDATE trips SET budget_agorot = :budgetAgorot, updated_at = :updatedAt, pending_sync = 1 " +
            "WHERE id = :id AND owner_id = :ownerId"
    )
    suspend fun setBudget(id: Long, ownerId: String, budgetAgorot: Long?, updatedAt: Long)

    @Query(
        "UPDATE trips SET currency_code = :currencyCode, updated_at = :updatedAt, pending_sync = 1 " +
            "WHERE id = :id AND owner_id = :ownerId"
    )
    suspend fun setCurrency(id: Long, ownerId: String, currencyCode: String?, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM trips WHERE owner_id = :ownerId")
    suspend fun count(ownerId: String): Int
}
