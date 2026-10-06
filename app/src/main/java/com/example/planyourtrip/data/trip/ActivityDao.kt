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
import java.time.LocalDate
import java.time.LocalTime

/**
 * Every query is scoped to the owner of the activity's trip, so an account never sees or changes another
 * account's activities. Ordering for display happens in Kotlin (`sortActivities`).
 */
@Dao
interface ActivityDao {
    @Query(
        "SELECT a.* FROM activities a JOIN trips t ON t.id = a.trip_id " +
            "WHERE a.trip_id = :tripId AND t.owner_id = :ownerId ORDER BY a.date, a.id"
    )
    fun observeForTrip(tripId: Long, ownerId: String): Flow<List<ActivityEntity>>

    @Query(
        "SELECT a.* FROM activities a JOIN trips t ON t.id = a.trip_id " +
            "WHERE a.id = :id AND t.owner_id = :ownerId"
    )
    fun observeById(id: Long, ownerId: String): Flow<ActivityEntity?>

    @Insert
    suspend fun insert(activity: ActivityEntity): Long

    @Query(
        "UPDATE activities SET date = :date, start_time = :startTime, title = :title, note = :note, " +
            "type = :type, location = :location, estimated_cost_agorot = :estimatedCostAgorot, booked = :booked, " +
            "updated_at = :updatedAt, pending_sync = 1 " +
            "WHERE id = :id AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun update(
        id: Long,
        ownerId: String,
        date: LocalDate,
        startTime: LocalTime?,
        title: String,
        note: String?,
        type: ActivityType,
        location: String?,
        estimatedCostAgorot: Long?,
        booked: Boolean,
        updatedAt: Long,
    )

    @Query("DELETE FROM activities WHERE id = :id AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)")
    suspend fun delete(id: Long, ownerId: String)

    @Query(
        "SELECT a.uuid AS uuid, t.uuid AS trip_uuid FROM activities a JOIN trips t ON t.id = a.trip_id " +
            "WHERE a.id = :id AND t.owner_id = :ownerId"
    )
    suspend fun ref(id: Long, ownerId: String): ChildRef?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletion(deletion: DeletionEntity)

    /** Deletes the activity and records the delete, so it reaches the cloud and the account's other devices. */
    @Transaction
    suspend fun deleteAndRecord(id: Long, ownerId: String, deletedAt: Long) {
        val ref = ref(id, ownerId) ?: return
        insertDeletion(DeletionEntity(ref.uuid, ownerId, SyncKind.ACTIVITY, ref.tripUuid, deletedAt))
        delete(id, ownerId)
    }
}
