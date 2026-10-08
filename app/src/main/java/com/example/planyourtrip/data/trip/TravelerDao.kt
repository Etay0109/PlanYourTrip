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

/** Every query is scoped to the owner of the traveler's trip. Travelers come in the order they were added. */
@Dao
interface TravelerDao {
    @Query(
        "SELECT r.* FROM travelers r JOIN trips t ON t.id = r.trip_id " +
            "WHERE r.trip_id = :tripId AND t.owner_id = :ownerId ORDER BY r.created_at, r.id"
    )
    fun observeForTrip(tripId: Long, ownerId: String): Flow<List<TravelerEntity>>

    /** How many travelers [ownerId]'s trip has (picks the next avatar color). */
    @Query(
        "SELECT COUNT(*) FROM travelers r JOIN trips t ON t.id = r.trip_id " +
            "WHERE r.trip_id = :tripId AND t.owner_id = :ownerId"
    )
    suspend fun count(tripId: Long, ownerId: String): Int

    @Query("SELECT MAX(created_at) FROM travelers WHERE trip_id = :tripId")
    suspend fun maxCreatedAt(tripId: Long): Long?

    @Insert
    suspend fun insert(traveler: TravelerEntity): Long

    @Query(
        "UPDATE travelers SET name = :name, updated_at = :updatedAt, pending_sync = 1 " +
            "WHERE id = :id AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun rename(id: Long, ownerId: String, name: String, updatedAt: Long)

    @Query(
        "SELECT r.uuid AS uuid, t.uuid AS trip_uuid FROM travelers r JOIN trips t ON t.id = r.trip_id " +
            "WHERE r.id = :id AND t.owner_id = :ownerId"
    )
    suspend fun ref(id: Long, ownerId: String): ChildRef?

    /** Whether an expense of [tripId] was paid by [key] or is split with it, or a settlement of the trip uses it. */
    @Query(
        "SELECT EXISTS(SELECT 1 FROM expenses WHERE trip_id = :tripId AND (paid_by = :key " +
            "OR instr(',' || split_between || ',', ',' || :key || ',') > 0)) " +
            "OR EXISTS(SELECT 1 FROM settlements WHERE trip_id = :tripId " +
            "AND (from_traveler = :key OR to_traveler = :key))"
    )
    suspend fun isUsed(tripId: Long, key: String): Boolean

    @Query("SELECT trip_id FROM travelers WHERE id = :id")
    suspend fun tripIdOf(id: Long): Long?

    @Query("DELETE FROM travelers WHERE id = :id AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)")
    suspend fun delete(id: Long, ownerId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletion(deletion: DeletionEntity)

    /**
     * Removes the traveler unless an expense or settlement of the trip uses them (checked in the same transaction),
     * and records the delete so it reaches the cloud.
     */
    @Transaction
    suspend fun removeIfUnused(id: Long, ownerId: String, deletedAt: Long): RemoveTravelerResult {
        val ref = ref(id, ownerId) ?: return RemoveTravelerResult.NOT_FOUND
        val tripId = tripIdOf(id) ?: return RemoveTravelerResult.NOT_FOUND
        if (isUsed(tripId, ref.uuid)) return RemoveTravelerResult.IN_USE
        insertDeletion(DeletionEntity(ref.uuid, ownerId, SyncKind.TRAVELER, ref.tripUuid, deletedAt))
        delete(id, ownerId)
        return RemoveTravelerResult.REMOVED
    }
}
