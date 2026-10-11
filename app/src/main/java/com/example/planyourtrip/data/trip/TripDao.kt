package com.example.planyourtrip.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.planyourtrip.data.sync.DeletionEntity
import com.example.planyourtrip.data.sync.SyncKind
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

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

    /** The trip's uuid (the link between a trip and its documents), or null when it isn't the owner's. */
    @Query("SELECT uuid FROM trips WHERE id = :id AND owner_id = :ownerId")
    suspend fun uuidOf(id: Long, ownerId: String): String?

    @Insert
    suspend fun insert(trip: TripEntity): Long

    @Query(
        "UPDATE trips SET destination = :destination, start_date = :startDate, end_date = :endDate, " +
            "currency_code = :currencyCode, country_code = :countryCode, latitude = :latitude, " +
            "longitude = :longitude, updated_at = :updatedAt, pending_sync = 1 " +
            "WHERE id = :id AND owner_id = :ownerId"
    )
    suspend fun update(
        id: Long,
        ownerId: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        currencyCode: String?,
        countryCode: String?,
        latitude: Double?,
        longitude: Double?,
        updatedAt: Long,
    )

    /** The budget is personal and synced on its own: it never changes the trip's `updated_at`. */
    @Query(
        "UPDATE trips SET budget_agorot = :budgetAgorot, budget_updated_at = :updatedAt, budget_pending = 1 " +
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

    @Query("DELETE FROM trips WHERE id = :id AND owner_id = :ownerId")
    suspend fun delete(id: Long, ownerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletion(deletion: DeletionEntity)

    /**
     * Deletes the trip (the foreign keys cascade to its expenses, activities and checklist items) and records the
     * delete, so it reaches the cloud and the account's other devices. Returns the trip's uuid, or null when it isn't
     * the owner's (nothing is deleted then).
     */
    @Transaction
    suspend fun deleteAndRecord(id: Long, ownerId: String, deletedAt: Long): String? {
        val uuid = uuidOf(id, ownerId) ?: return null
        insertDeletion(DeletionEntity(uuid, ownerId, SyncKind.TRIP, null, deletedAt))
        delete(id, ownerId)
        return uuid
    }
}
