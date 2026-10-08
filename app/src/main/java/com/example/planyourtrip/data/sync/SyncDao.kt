package com.example.planyourtrip.data.sync

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.planyourtrip.data.trip.ActivityEntity
import com.example.planyourtrip.data.trip.ChecklistItemEntity
import com.example.planyourtrip.data.trip.ExpenseEntity
import com.example.planyourtrip.data.trip.SettlementEntity
import com.example.planyourtrip.data.trip.TravelerEntity
import com.example.planyourtrip.data.trip.TripEntity
import kotlinx.coroutines.flow.Flow

/** Room queries of the sync layer. Like every other DAO, everything is scoped to one owner. */
@Dao
interface SyncDao {
    @Query("SELECT * FROM trips WHERE owner_id = :ownerId AND (:pendingOnly = 0 OR pending_sync = 1)")
    suspend fun trips(ownerId: String, pendingOnly: Boolean): List<TripEntity>

    @Query(
        "SELECT e.*, t.uuid AS trip_uuid FROM expenses e JOIN trips t ON t.id = e.trip_id " +
            "WHERE t.owner_id = :ownerId AND (:pendingOnly = 0 OR e.pending_sync = 1)"
    )
    suspend fun expenses(ownerId: String, pendingOnly: Boolean): List<ExpenseRow>

    @Query(
        "SELECT a.*, t.uuid AS trip_uuid FROM activities a JOIN trips t ON t.id = a.trip_id " +
            "WHERE t.owner_id = :ownerId AND (:pendingOnly = 0 OR a.pending_sync = 1)"
    )
    suspend fun activities(ownerId: String, pendingOnly: Boolean): List<ActivityRow>

    @Query(
        "SELECT c.*, t.uuid AS trip_uuid FROM checklist_items c JOIN trips t ON t.id = c.trip_id " +
            "WHERE t.owner_id = :ownerId AND (:pendingOnly = 0 OR c.pending_sync = 1)"
    )
    suspend fun checklistItems(ownerId: String, pendingOnly: Boolean): List<ChecklistRow>

    @Query(
        "SELECT r.*, t.uuid AS trip_uuid FROM travelers r JOIN trips t ON t.id = r.trip_id " +
            "WHERE t.owner_id = :ownerId AND (:pendingOnly = 0 OR r.pending_sync = 1)"
    )
    suspend fun travelers(ownerId: String, pendingOnly: Boolean): List<TravelerRow>

    @Query(
        "SELECT s.*, t.uuid AS trip_uuid FROM settlements s JOIN trips t ON t.id = s.trip_id " +
            "WHERE t.owner_id = :ownerId AND (:pendingOnly = 0 OR s.pending_sync = 1)"
    )
    suspend fun settlements(ownerId: String, pendingOnly: Boolean): List<SettlementRow>

    @Query("SELECT * FROM deletions WHERE owner_id = :ownerId")
    suspend fun deletions(ownerId: String): List<DeletionEntity>

    @Query(
        "SELECT (SELECT COUNT(*) FROM trips WHERE owner_id = :ownerId AND pending_sync = 1)" +
            " + (SELECT COUNT(*) FROM expenses e JOIN trips t ON t.id = e.trip_id" +
            " WHERE t.owner_id = :ownerId AND e.pending_sync = 1)" +
            " + (SELECT COUNT(*) FROM activities a JOIN trips t ON t.id = a.trip_id" +
            " WHERE t.owner_id = :ownerId AND a.pending_sync = 1)" +
            " + (SELECT COUNT(*) FROM checklist_items c JOIN trips t ON t.id = c.trip_id" +
            " WHERE t.owner_id = :ownerId AND c.pending_sync = 1)" +
            " + (SELECT COUNT(*) FROM travelers r JOIN trips t ON t.id = r.trip_id" +
            " WHERE t.owner_id = :ownerId AND r.pending_sync = 1)" +
            " + (SELECT COUNT(*) FROM settlements s JOIN trips t ON t.id = s.trip_id" +
            " WHERE t.owner_id = :ownerId AND s.pending_sync = 1)" +
            " + (SELECT COUNT(*) FROM deletions WHERE owner_id = :ownerId)"
    )
    fun pendingCount(ownerId: String): Flow<Int>

    @Query("SELECT * FROM trips WHERE uuid = :uuid")
    suspend fun tripByUuid(uuid: String): TripEntity?

    @Query("SELECT * FROM expenses WHERE uuid = :uuid")
    suspend fun expenseByUuid(uuid: String): ExpenseEntity?

    @Query("SELECT * FROM activities WHERE uuid = :uuid")
    suspend fun activityByUuid(uuid: String): ActivityEntity?

    @Query("SELECT * FROM checklist_items WHERE uuid = :uuid")
    suspend fun checklistItemByUuid(uuid: String): ChecklistItemEntity?

    @Query("SELECT * FROM travelers WHERE uuid = :uuid")
    suspend fun travelerByUuid(uuid: String): TravelerEntity?

    @Query("SELECT * FROM settlements WHERE uuid = :uuid")
    suspend fun settlementByUuid(uuid: String): SettlementEntity?

    @Query("SELECT * FROM deletions WHERE uuid = :uuid")
    suspend fun deletion(uuid: String): DeletionEntity?

    @Insert
    suspend fun insertTrip(trip: TripEntity)

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Insert
    suspend fun insertExpense(expense: ExpenseEntity)

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Insert
    suspend fun insertActivity(activity: ActivityEntity)

    @Update
    suspend fun updateActivity(activity: ActivityEntity)

    @Insert
    suspend fun insertChecklistItem(item: ChecklistItemEntity)

    @Update
    suspend fun updateChecklistItem(item: ChecklistItemEntity)

    @Insert
    suspend fun insertTraveler(traveler: TravelerEntity)

    @Update
    suspend fun updateTraveler(traveler: TravelerEntity)

    @Insert
    suspend fun insertSettlement(settlement: SettlementEntity)

    @Update
    suspend fun updateSettlement(settlement: SettlementEntity)

    /** Deleting a trip also deletes all its children (foreign key CASCADE). */
    @Query("DELETE FROM trips WHERE uuid = :uuid AND owner_id = :ownerId AND updated_at = :updatedAt")
    suspend fun deleteTrip(uuid: String, ownerId: String, updatedAt: Long)

    @Query(
        "DELETE FROM expenses WHERE uuid = :uuid AND updated_at = :updatedAt " +
            "AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun deleteExpense(uuid: String, ownerId: String, updatedAt: Long)

    @Query(
        "DELETE FROM activities WHERE uuid = :uuid AND updated_at = :updatedAt " +
            "AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun deleteActivity(uuid: String, ownerId: String, updatedAt: Long)

    @Query(
        "DELETE FROM checklist_items WHERE uuid = :uuid AND updated_at = :updatedAt " +
            "AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun deleteChecklistItem(uuid: String, ownerId: String, updatedAt: Long)

    @Query(
        "DELETE FROM travelers WHERE uuid = :uuid AND updated_at = :updatedAt " +
            "AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun deleteTraveler(uuid: String, ownerId: String, updatedAt: Long)

    @Query(
        "DELETE FROM settlements WHERE uuid = :uuid AND updated_at = :updatedAt " +
            "AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun deleteSettlement(uuid: String, ownerId: String, updatedAt: Long)

    @Query("UPDATE trips SET pending_sync = 0 WHERE uuid = :uuid AND owner_id = :ownerId AND updated_at = :updatedAt")
    suspend fun markTripSynced(uuid: String, ownerId: String, updatedAt: Long)

    @Query(
        "UPDATE expenses SET pending_sync = 0 WHERE uuid = :uuid AND updated_at = :updatedAt " +
            "AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun markExpenseSynced(uuid: String, ownerId: String, updatedAt: Long)

    @Query(
        "UPDATE activities SET pending_sync = 0 WHERE uuid = :uuid AND updated_at = :updatedAt " +
            "AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun markActivitySynced(uuid: String, ownerId: String, updatedAt: Long)

    @Query(
        "UPDATE checklist_items SET pending_sync = 0 WHERE uuid = :uuid AND updated_at = :updatedAt " +
            "AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun markChecklistItemSynced(uuid: String, ownerId: String, updatedAt: Long)

    @Query(
        "UPDATE travelers SET pending_sync = 0 WHERE uuid = :uuid AND updated_at = :updatedAt " +
            "AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun markTravelerSynced(uuid: String, ownerId: String, updatedAt: Long)

    @Query(
        "UPDATE settlements SET pending_sync = 0 WHERE uuid = :uuid AND updated_at = :updatedAt " +
            "AND trip_id IN (SELECT id FROM trips WHERE owner_id = :ownerId)"
    )
    suspend fun markSettlementSynced(uuid: String, ownerId: String, updatedAt: Long)

    @Query("DELETE FROM deletions WHERE uuid = :uuid AND owner_id = :ownerId AND deleted_at = :deletedAt")
    suspend fun dropDeletion(uuid: String, ownerId: String, deletedAt: Long)

    @Query("DELETE FROM deletions WHERE uuid = :uuid")
    suspend fun dropDeletion(uuid: String)

    @Query("DELETE FROM trips WHERE owner_id = :ownerId")
    suspend fun deleteTripsOf(ownerId: String)

    @Query("DELETE FROM deletions WHERE owner_id = :ownerId")
    suspend fun deleteDeletionsOf(ownerId: String)
}

data class ExpenseRow(
    @Embedded val expense: ExpenseEntity,
    @ColumnInfo(name = "trip_uuid") val tripUuid: String,
)

data class ActivityRow(
    @Embedded val activity: ActivityEntity,
    @ColumnInfo(name = "trip_uuid") val tripUuid: String,
)

data class ChecklistRow(
    @Embedded val item: ChecklistItemEntity,
    @ColumnInfo(name = "trip_uuid") val tripUuid: String,
)

data class TravelerRow(
    @Embedded val traveler: TravelerEntity,
    @ColumnInfo(name = "trip_uuid") val tripUuid: String,
)

data class SettlementRow(
    @Embedded val settlement: SettlementEntity,
    @ColumnInfo(name = "trip_uuid") val tripUuid: String,
)
