package com.example.planyourtrip.data.sync

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.planyourtrip.data.trip.ActivityEntity
import com.example.planyourtrip.data.trip.ChecklistItemEntity
import com.example.planyourtrip.data.trip.ExpenseEntity
import com.example.planyourtrip.data.trip.SettlementEntity
import com.example.planyourtrip.data.trip.TravelerEntity
import com.example.planyourtrip.data.trip.TripEntity
import com.example.planyourtrip.data.trip.TripMemberEntity
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

    /** The owner's trips whose own budget was ever set (a [SyncKind.BUDGET] item each). */
    @Query(
        "SELECT * FROM trips WHERE owner_id = :ownerId AND budget_updated_at > 0 " +
            "AND (:pendingOnly = 0 OR budget_pending = 1)"
    )
    suspend fun budgets(ownerId: String, pendingOnly: Boolean): List<TripEntity>

    @Query("SELECT * FROM deletions WHERE owner_id = :ownerId")
    suspend fun deletions(ownerId: String): List<DeletionEntity>

    @Query(
        "SELECT (SELECT COUNT(*) FROM trips WHERE owner_id = :ownerId AND pending_sync = 1)" +
            " + (SELECT COUNT(*) FROM trips WHERE owner_id = :ownerId AND budget_pending = 1)" +
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

    @Query("SELECT * FROM trips WHERE uuid = :uuid AND owner_id = :ownerId")
    suspend fun tripByUuid(uuid: String, ownerId: String): TripEntity?

    @Query(
        "SELECT e.* FROM expenses e JOIN trips t ON t.id = e.trip_id " +
            "WHERE e.uuid = :uuid AND t.uuid = :tripUuid AND t.owner_id = :ownerId"
    )
    suspend fun expenseByUuid(uuid: String, tripUuid: String, ownerId: String): ExpenseEntity?

    @Query(
        "SELECT a.* FROM activities a JOIN trips t ON t.id = a.trip_id " +
            "WHERE a.uuid = :uuid AND t.uuid = :tripUuid AND t.owner_id = :ownerId"
    )
    suspend fun activityByUuid(uuid: String, tripUuid: String, ownerId: String): ActivityEntity?

    @Query(
        "SELECT c.* FROM checklist_items c JOIN trips t ON t.id = c.trip_id " +
            "WHERE c.uuid = :uuid AND t.uuid = :tripUuid AND t.owner_id = :ownerId"
    )
    suspend fun checklistItemByUuid(uuid: String, tripUuid: String, ownerId: String): ChecklistItemEntity?

    @Query(
        "SELECT r.* FROM travelers r JOIN trips t ON t.id = r.trip_id " +
            "WHERE r.uuid = :uuid AND t.uuid = :tripUuid AND t.owner_id = :ownerId"
    )
    suspend fun travelerByUuid(uuid: String, tripUuid: String, ownerId: String): TravelerEntity?

    @Query(
        "SELECT s.* FROM settlements s JOIN trips t ON t.id = s.trip_id " +
            "WHERE s.uuid = :uuid AND t.uuid = :tripUuid AND t.owner_id = :ownerId"
    )
    suspend fun settlementByUuid(uuid: String, tripUuid: String, ownerId: String): SettlementEntity?

    @Query("SELECT * FROM deletions WHERE uuid = :uuid AND owner_id = :ownerId")
    suspend fun deletion(uuid: String, ownerId: String): DeletionEntity?

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
        "UPDATE trips SET budget_pending = 0 " +
            "WHERE uuid = :tripUuid AND owner_id = :ownerId AND budget_updated_at = :updatedAt"
    )
    suspend fun markBudgetSynced(tripUuid: String, ownerId: String, updatedAt: Long)

    /** Stores the cloud's version of the owner's budget, unless this device has a newer one. */
    @Query(
        "UPDATE trips SET budget_agorot = :budgetAgorot, budget_updated_at = :updatedAt, budget_pending = 0 " +
            "WHERE uuid = :tripUuid AND owner_id = :ownerId AND budget_updated_at <= :updatedAt"
    )
    suspend fun saveBudget(tripUuid: String, ownerId: String, budgetAgorot: Long?, updatedAt: Long)

    /** The budget is gone from the cloud (and wasn't changed here since): no budget. */
    @Query(
        "UPDATE trips SET budget_agorot = NULL, budget_updated_at = 0, budget_pending = 0 " +
            "WHERE uuid = :tripUuid AND owner_id = :ownerId AND budget_updated_at = :updatedAt"
    )
    suspend fun clearBudget(tripUuid: String, ownerId: String, updatedAt: Long)

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

    @Query("DELETE FROM deletions WHERE uuid = :uuid AND owner_id = :ownerId")
    suspend fun dropDeletion(uuid: String, ownerId: String)

    @Query("DELETE FROM trips WHERE owner_id = :ownerId")
    suspend fun deleteTripsOf(ownerId: String)

    @Query("SELECT uuid, cloud_owner_id FROM trips WHERE owner_id = :ownerId")
    suspend fun tripOwners(ownerId: String): List<TripOwnerRow>

    /** Removes the owner's copies of these trips; their items go with them (foreign key CASCADE). */
    @Query("DELETE FROM trips WHERE owner_id = :ownerId AND uuid IN (:tripUuids)")
    suspend fun deleteTrips(ownerId: String, tripUuids: List<String>)

    /** Drops the owner's tombstones of these trips and of their items (deletes that will never be uploaded). */
    @Query(
        "DELETE FROM deletions WHERE owner_id = :ownerId " +
            "AND (trip_uuid IN (:tripUuids) OR (kind = 'TRIP' AND uuid IN (:tripUuids)))"
    )
    suspend fun deleteDeletionsOfTrips(ownerId: String, tripUuids: List<String>)

    @Query("DELETE FROM trip_members WHERE trip_id = :tripId")
    suspend fun deleteMembersOfTrip(tripId: Long)

    @Insert
    suspend fun insertMembers(members: List<TripMemberEntity>)

    @Query("DELETE FROM deletions WHERE owner_id = :ownerId")
    suspend fun deleteDeletionsOf(ownerId: String)
}

data class TripOwnerRow(
    val uuid: String,
    @ColumnInfo(name = "cloud_owner_id") val cloudOwnerId: String,
)

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
