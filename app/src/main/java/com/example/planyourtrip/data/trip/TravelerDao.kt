package com.example.planyourtrip.data.trip

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
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

    // ---------- Merging a name-only traveler into a member (TravelerMerge.kt) ----------

    @Query("SELECT * FROM trips WHERE id = :tripId AND owner_id = :ownerId")
    suspend fun trip(tripId: Long, ownerId: String): TripEntity?

    @Query("SELECT * FROM travelers WHERE trip_id = :tripId ORDER BY created_at, id")
    suspend fun travelersOf(tripId: Long): List<TravelerEntity>

    @Query("SELECT * FROM expenses WHERE trip_id = :tripId")
    suspend fun expensesOf(tripId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM settlements WHERE trip_id = :tripId")
    suspend fun settlementsOf(tripId: Long): List<SettlementEntity>

    @Update
    suspend fun updateExpenses(expenses: List<ExpenseEntity>)

    @Update
    suspend fun updateSettlements(settlements: List<SettlementEntity>)

    @Query("DELETE FROM settlements WHERE id = :id")
    suspend fun deleteSettlement(id: Long)

    /**
     * Merges traveler [travelerId] into member [memberUid] of [ownerId]'s trip [tripId], in one transaction: [plan]
     * gets the trip's current rows, and only a [TravelerMergePlan.Ready] is written. Changed expenses and settlements
     * become pending edits (stamped [now]); deleted settlements and the traveler are recorded deletes, so all of it
     * reaches the cloud with the next sync.
     */
    @Transaction
    suspend fun mergeIntoMember(
        tripId: Long,
        ownerId: String,
        travelerId: Long,
        memberUid: String,
        now: Long,
        plan: (TravelerMergeInput) -> TravelerMergePlan,
    ): MergeTravelerResult {
        val trip = trip(tripId, ownerId) ?: return MergeTravelerResult.NOT_FOUND
        val travelers = travelersOf(tripId)
        val traveler = travelers.firstOrNull { it.id == travelerId } ?: return MergeTravelerResult.NOT_FOUND
        if (memberUid !in trip.memberIds) return MergeTravelerResult.NOT_FOUND
        val expenses = expensesOf(tripId)
        val settlements = settlementsOf(tripId)
        val input = TravelerMergeInput(
            travelerKey = traveler.uuid,
            memberKey = memberUid,
            me = ownerId,
            memberIds = trip.memberIds,
            travelers = travelers.map { it.toTraveler() },
            expenses = expenses.map { it.toExpense() },
            settlements = settlements.map { it.toSettlement() },
        )
        val changes = (plan(input) as? TravelerMergePlan.Ready)?.changes ?: return MergeTravelerResult.BLOCKED
        val expenseRows = expenses.associateBy { it.id }
        updateExpenses(
            changes.expenses.map { e ->
                expenseRows.getValue(e.id).copy(
                    paidBy = e.paidBy, splitBetween = e.splitBetween, updatedAt = now, pendingSync = true,
                )
            },
        )
        val settlementRows = settlements.associateBy { it.id }
        updateSettlements(
            changes.settlements.map { s ->
                settlementRows.getValue(s.id).copy(fromKey = s.fromKey, toKey = s.toKey, updatedAt = now, pendingSync = true)
            },
        )
        for (id in changes.deletedSettlementIds) {
            val row = settlementRows.getValue(id)
            insertDeletion(DeletionEntity(row.uuid, ownerId, SyncKind.SETTLEMENT, trip.uuid, now))
            deleteSettlement(id)
        }
        insertDeletion(DeletionEntity(traveler.uuid, ownerId, SyncKind.TRAVELER, trip.uuid, now))
        delete(travelerId, ownerId)
        return MergeTravelerResult.MERGED
    }
}
