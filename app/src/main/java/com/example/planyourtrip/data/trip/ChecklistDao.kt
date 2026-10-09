package com.example.planyourtrip.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.planyourtrip.data.sync.ChildRef
import com.example.planyourtrip.data.sync.DeletionEntity
import com.example.planyourtrip.data.sync.SyncKind
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Every query is scoped to the owner of the item's trip, so an account never sees or changes another account's
 * checklist items. Items come in the order they were added (`created_at, id`): [insertInOrder] gives every new item
 * a later `created_at` than any item of its trip, so two items never tie on this device.
 */
@Dao
interface ChecklistDao {
    @Query(
        "SELECT c.* FROM checklist_items c JOIN trips t ON t.id = c.trip_id " +
            "WHERE c.trip_id = :tripId AND t.owner_id = :ownerId ORDER BY c.created_at, c.id"
    )
    fun observeForTrip(tripId: Long, ownerId: String): Flow<List<ChecklistItemEntity>>

    @Query(
        "SELECT c.* FROM checklist_items c JOIN trips t ON t.id = c.trip_id " +
            "WHERE c.id = :id AND t.owner_id = :ownerId"
    )
    suspend fun byId(id: Long, ownerId: String): ChecklistItemEntity?

    @Insert
    suspend fun insert(item: ChecklistItemEntity): Long

    @Insert
    suspend fun insertAll(items: List<ChecklistItemEntity>): List<Long>

    @Query("SELECT MAX(created_at) FROM checklist_items WHERE trip_id = :tripId")
    suspend fun maxCreatedAt(tripId: Long): Long?

    /**
     * Adds [items] (all of trip [tripId]) at the end of the trip's lists, in the given order: the first gets [now], or
     * 1 ms after the trip's latest item when that is later (same millisecond, a clock set back), and every next one
     * 1 ms more. Returns the new ids.
     */
    @Transaction
    suspend fun insertInOrder(tripId: Long, items: List<ChecklistItemEntity>, now: Long): List<Long> {
        val latest = maxCreatedAt(tripId)
        val start = if (latest == null) now else maxOf(now, latest + 1)
        return insertAll(items.mapIndexed { i, item -> item.copy(tripId = tripId, createdAt = start + i) })
    }

    @Query(
        "UPDATE checklist_items SET checked = :checked, updated_at = :updatedAt, pending_sync = 1 " +
            "WHERE id = :id AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun setChecked(id: Long, ownerId: String, checked: Boolean, updatedAt: Long)

    @Query(
        "UPDATE checklist_items SET text = :text, updated_at = :updatedAt, pending_sync = 1 " +
            "WHERE id = :id AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun setText(id: Long, ownerId: String, text: String, updatedAt: Long)

    /** Only "Before the trip" items have a due date. */
    @Query(
        "UPDATE checklist_items SET due_date = :dueDate, updated_at = :updatedAt, pending_sync = 1 " +
            "WHERE id = :id AND list = 'BEFORE_TRIP' AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun setDueDate(id: Long, ownerId: String, dueDate: LocalDate?, updatedAt: Long)

    @Query(
        "DELETE FROM checklist_items WHERE id = :id " +
            "AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun delete(id: Long, ownerId: String)

    @Query(
        "SELECT c.uuid AS uuid, t.uuid AS trip_uuid FROM checklist_items c JOIN trips t ON t.id = c.trip_id " +
            "WHERE c.id = :id AND t.owner_id = :ownerId"
    )
    suspend fun ref(id: Long, ownerId: String): ChildRef?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletion(deletion: DeletionEntity)

    /**
     * Deletes the item and records the delete, so it reaches the cloud and the account's other devices. A Packing
     * item is personal (`SyncKind.PACKING`, the member's own cloud folder); a "Before the trip" item is shared.
     */
    @Transaction
    suspend fun deleteAndRecord(id: Long, ownerId: String, deletedAt: Long) {
        val ref = ref(id, ownerId) ?: return
        val kind = if (byId(id, ownerId)?.list == ChecklistKind.PACKING) SyncKind.PACKING else SyncKind.CHECKLIST
        insertDeletion(DeletionEntity(ref.uuid, ownerId, kind, ref.tripUuid, deletedAt))
        delete(id, ownerId)
    }
}
