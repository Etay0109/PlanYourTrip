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

/** Every query is scoped to the owner of the settlement's trip. Settlements come newest first. */
@Dao
interface SettlementDao {
    @Query(
        "SELECT s.* FROM settlements s JOIN trips t ON t.id = s.trip_id " +
            "WHERE s.trip_id = :tripId AND t.owner_id = :ownerId ORDER BY s.date DESC, s.created_at DESC, s.id DESC"
    )
    fun observeForTrip(tripId: Long, ownerId: String): Flow<List<SettlementEntity>>

    @Insert
    suspend fun insert(settlement: SettlementEntity): Long

    @Query("DELETE FROM settlements WHERE id = :id AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)")
    suspend fun delete(id: Long, ownerId: String)

    @Query(
        "SELECT s.uuid AS uuid, t.uuid AS trip_uuid FROM settlements s JOIN trips t ON t.id = s.trip_id " +
            "WHERE s.id = :id AND t.owner_id = :ownerId"
    )
    suspend fun ref(id: Long, ownerId: String): ChildRef?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletion(deletion: DeletionEntity)

    /** Deletes the settlement (the debt it paid reopens) and records the delete for the cloud. */
    @Transaction
    suspend fun deleteAndRecord(id: Long, ownerId: String, deletedAt: Long) {
        val ref = ref(id, ownerId) ?: return
        insertDeletion(DeletionEntity(ref.uuid, ownerId, SyncKind.SETTLEMENT, ref.tripUuid, deletedAt))
        delete(id, ownerId)
    }
}
