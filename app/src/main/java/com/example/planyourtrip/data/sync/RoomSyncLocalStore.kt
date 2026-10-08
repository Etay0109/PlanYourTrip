package com.example.planyourtrip.data.sync

import androidx.room.withTransaction
import com.example.planyourtrip.data.trip.ActivityEntity
import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.AppDatabase
import com.example.planyourtrip.data.trip.ChecklistItemEntity
import com.example.planyourtrip.data.trip.ExpenseEntity
import com.example.planyourtrip.data.trip.SettlementEntity
import com.example.planyourtrip.data.trip.TravelerEntity
import com.example.planyourtrip.data.trip.TripEntity
import com.example.planyourtrip.data.trip.toExpense
import kotlinx.coroutines.flow.Flow

class RoomSyncLocalStore(private val db: AppDatabase) : SyncLocalStore {
    private val dao = db.syncDao()

    override suspend fun snapshot(ownerId: String, pendingOnly: Boolean): List<LocalItem> = db.withTransaction {
        dao.trips(ownerId, pendingOnly).map { LocalItem(it.toSyncItem(), it.pendingSync) } +
            dao.expenses(ownerId, pendingOnly).map { LocalItem(it.toSyncItem(), it.expense.pendingSync) } +
            dao.activities(ownerId, pendingOnly).map { LocalItem(it.toSyncItem(), it.activity.pendingSync) } +
            dao.checklistItems(ownerId, pendingOnly).map { LocalItem(it.toSyncItem(), it.item.pendingSync) } +
            dao.travelers(ownerId, pendingOnly).map { LocalItem(it.toSyncItem(), it.traveler.pendingSync) } +
            dao.settlements(ownerId, pendingOnly).map { LocalItem(it.toSyncItem(), it.settlement.pendingSync) } +
            dao.deletions(ownerId).map { LocalItem(it.toSyncItem(), pending = true) }
    }

    override suspend fun apply(ownerId: String, plan: MergePlan) = db.withTransaction {
        plan.saveLocally.forEach { save(ownerId, it) }
        plan.deleteLocally.forEach { item ->
            when (item.kind) {
                SyncKind.TRIP -> dao.deleteTrip(item.uuid, ownerId, item.updatedAt)
                SyncKind.EXPENSE -> dao.deleteExpense(item.uuid, ownerId, item.updatedAt)
                SyncKind.ACTIVITY -> dao.deleteActivity(item.uuid, ownerId, item.updatedAt)
                SyncKind.CHECKLIST -> dao.deleteChecklistItem(item.uuid, ownerId, item.updatedAt)
                SyncKind.TRAVELER -> dao.deleteTraveler(item.uuid, ownerId, item.updatedAt)
                SyncKind.SETTLEMENT -> dao.deleteSettlement(item.uuid, ownerId, item.updatedAt)
            }
        }
        plan.dropTombstones.forEach { dao.dropDeletion(it.uuid, ownerId, it.updatedAt) }
    }

    override suspend fun markSynced(ownerId: String, item: SyncItem) = when {
        item.isDeleted -> dao.dropDeletion(item.uuid, ownerId, item.updatedAt)
        else -> when (item.kind) {
            SyncKind.TRIP -> dao.markTripSynced(item.uuid, ownerId, item.updatedAt)
            SyncKind.EXPENSE -> dao.markExpenseSynced(item.uuid, ownerId, item.updatedAt)
            SyncKind.ACTIVITY -> dao.markActivitySynced(item.uuid, ownerId, item.updatedAt)
            SyncKind.CHECKLIST -> dao.markChecklistItemSynced(item.uuid, ownerId, item.updatedAt)
            SyncKind.TRAVELER -> dao.markTravelerSynced(item.uuid, ownerId, item.updatedAt)
            SyncKind.SETTLEMENT -> dao.markSettlementSynced(item.uuid, ownerId, item.updatedAt)
        }
    }

    override fun pendingCount(ownerId: String): Flow<Int> = dao.pendingCount(ownerId)

    override suspend fun deleteAllFor(ownerId: String) = db.withTransaction {
        // Travelers, expenses, activities, checklist items and settlements go with their trips (foreign key CASCADE). Documents are not
        // synced and stay on the device (hidden by owner), so they are not deleted here.
        dao.deleteTripsOf(ownerId)
        dao.deleteDeletionsOf(ownerId)
    }

    /** Stores a cloud version as synced, unless this device has a newer change (edit or delete) of the item. */
    private suspend fun save(ownerId: String, item: SyncItem) {
        val data = item.data ?: return
        val tombstone = dao.deletion(item.uuid)
        if (tombstone != null) {
            if (tombstone.deletedAt > item.updatedAt) return
            dao.dropDeletion(item.uuid)
        }
        when (data) {
            is ItemData.TripData -> {
                val existing = dao.tripByUuid(item.uuid)
                if (existing != null && (existing.ownerId != ownerId || existing.updatedAt > item.updatedAt)) return
                val trip = data.toEntity(existing?.id ?: 0, ownerId, item)
                if (existing == null) dao.insertTrip(trip) else dao.updateTrip(trip)
            }
            is ItemData.ExpenseData -> {
                val existing = dao.expenseByUuid(item.uuid)
                val tripId = childTripId(ownerId, item, existing?.updatedAt) ?: return
                val expense = data.toEntity(existing?.id ?: 0, tripId, item)
                if (existing == null) dao.insertExpense(expense) else dao.updateExpense(expense)
            }
            is ItemData.ActivityData -> {
                val existing = dao.activityByUuid(item.uuid)
                val tripId = childTripId(ownerId, item, existing?.updatedAt) ?: return
                val activity = data.toEntity(existing?.id ?: 0, tripId, item)
                if (existing == null) dao.insertActivity(activity) else dao.updateActivity(activity)
            }
            is ItemData.ChecklistData -> {
                val existing = dao.checklistItemByUuid(item.uuid)
                val tripId = childTripId(ownerId, item, existing?.updatedAt) ?: return
                val checklistItem = data.toEntity(existing?.id ?: 0, tripId, item)
                if (existing == null) dao.insertChecklistItem(checklistItem) else dao.updateChecklistItem(checklistItem)
            }
            is ItemData.TravelerData -> {
                val existing = dao.travelerByUuid(item.uuid)
                val tripId = childTripId(ownerId, item, existing?.updatedAt) ?: return
                val traveler = data.toEntity(existing?.id ?: 0, tripId, item)
                if (existing == null) dao.insertTraveler(traveler) else dao.updateTraveler(traveler)
            }
            is ItemData.SettlementData -> {
                val existing = dao.settlementByUuid(item.uuid)
                val tripId = childTripId(ownerId, item, existing?.updatedAt) ?: return
                val settlement = data.toEntity(existing?.id ?: 0, tripId, item)
                if (existing == null) dao.insertSettlement(settlement) else dao.updateSettlement(settlement)
            }
        }
    }

    /**
     * Local id of a child item's trip, or null when the cloud version must not be stored: the trip isn't on this
     * device for this owner, or the local row ([localUpdatedAt], null = none) has a newer change.
     */
    private suspend fun childTripId(ownerId: String, item: SyncItem, localUpdatedAt: Long?): Long? {
        val tripId = ownedTripId(ownerId, item) ?: return null
        return tripId.takeIf { localUpdatedAt == null || localUpdatedAt <= item.updatedAt }
    }

    /** Local id of the item's trip, or null when the trip isn't on this device for this owner. */
    private suspend fun ownedTripId(ownerId: String, item: SyncItem): Long? {
        val trip = dao.tripByUuid(item.tripUuid ?: return null) ?: return null
        return trip.id.takeIf { trip.ownerId == ownerId }
    }
}

// ---------- Cloud versions → Room rows (stored as synced) ----------

private fun ItemData.TripData.toEntity(id: Long, ownerId: String, item: SyncItem) = TripEntity(
    id = id,
    ownerId = ownerId,
    destination = destination,
    startDate = startDate,
    endDate = endDate,
    color = color,
    budgetAgorot = budgetAgorot,
    currencyCode = currencyCode,
    uuid = item.uuid,
    updatedAt = item.updatedAt,
    pendingSync = false,
)

private fun ItemData.ExpenseData.toEntity(id: Long, tripId: Long, item: SyncItem) = ExpenseEntity(
    id = id,
    tripId = tripId,
    name = name,
    amountAgorot = amountAgorot,
    category = category,
    date = date,
    originalAmount = original?.amount,
    originalCurrency = original?.currencyCode,
    exchangeRate = original?.ilsPerUnit,
    rateDate = original?.rateDate,
    paidBy = paidBy,
    splitBetween = splitBetween,
    uuid = item.uuid,
    updatedAt = item.updatedAt,
    pendingSync = false,
)

private fun ItemData.ActivityData.toEntity(id: Long, tripId: Long, item: SyncItem) = ActivityEntity(
    id = id,
    tripId = tripId,
    date = input.date,
    startTime = input.startTime,
    title = input.title,
    note = input.note,
    type = input.type,
    location = input.location,
    estimatedCostAgorot = input.estimatedCostAgorot,
    booked = input.booked,
    uuid = item.uuid,
    updatedAt = item.updatedAt,
    pendingSync = false,
)

private fun ItemData.ChecklistData.toEntity(id: Long, tripId: Long, item: SyncItem) = ChecklistItemEntity(
    id = id,
    tripId = tripId,
    list = list,
    text = text,
    checked = checked,
    dueDate = dueDate,
    createdAt = createdAt,
    uuid = item.uuid,
    updatedAt = item.updatedAt,
    pendingSync = false,
)

private fun ItemData.TravelerData.toEntity(id: Long, tripId: Long, item: SyncItem) = TravelerEntity(
    id = id,
    tripId = tripId,
    name = name,
    color = color,
    createdAt = createdAt,
    uuid = item.uuid,
    updatedAt = item.updatedAt,
    pendingSync = false,
)

private fun ItemData.SettlementData.toEntity(id: Long, tripId: Long, item: SyncItem) = SettlementEntity(
    id = id,
    tripId = tripId,
    fromKey = fromKey,
    toKey = toKey,
    amountAgorot = amountAgorot,
    date = date,
    createdAt = createdAt,
    uuid = item.uuid,
    updatedAt = item.updatedAt,
    pendingSync = false,
)

// ---------- Room rows → sync items ----------

private fun TripEntity.toSyncItem() = SyncItem(
    kind = SyncKind.TRIP,
    uuid = uuid,
    tripUuid = null,
    updatedAt = updatedAt,
    data = ItemData.TripData(destination, startDate, endDate, color, budgetAgorot, currencyCode),
)

private fun ExpenseRow.toSyncItem(): SyncItem {
    val domain = expense.toExpense()
    return SyncItem(
        kind = SyncKind.EXPENSE,
        uuid = expense.uuid,
        tripUuid = tripUuid,
        updatedAt = expense.updatedAt,
        data = ItemData.ExpenseData(
            domain.name, domain.amountAgorot, domain.category, domain.date, domain.original,
            domain.paidBy, domain.splitBetween,
        ),
    )
}

private fun ActivityRow.toSyncItem() = SyncItem(
    kind = SyncKind.ACTIVITY,
    uuid = activity.uuid,
    tripUuid = tripUuid,
    updatedAt = activity.updatedAt,
    data = ItemData.ActivityData(
        with(activity) { ActivityInput(date, startTime, title, type, note, location, estimatedCostAgorot, booked) },
    ),
)

private fun ChecklistRow.toSyncItem() = SyncItem(
    kind = SyncKind.CHECKLIST,
    uuid = item.uuid,
    tripUuid = tripUuid,
    updatedAt = item.updatedAt,
    data = with(item) { ItemData.ChecklistData(list, text, checked, dueDate, createdAt) },
)

private fun TravelerRow.toSyncItem() = SyncItem(
    kind = SyncKind.TRAVELER,
    uuid = traveler.uuid,
    tripUuid = tripUuid,
    updatedAt = traveler.updatedAt,
    data = with(traveler) { ItemData.TravelerData(name, color, createdAt) },
)

private fun SettlementRow.toSyncItem() = SyncItem(
    kind = SyncKind.SETTLEMENT,
    uuid = settlement.uuid,
    tripUuid = tripUuid,
    updatedAt = settlement.updatedAt,
    data = with(settlement) { ItemData.SettlementData(fromKey, toKey, amountAgorot, date, createdAt) },
)

private fun DeletionEntity.toSyncItem() =
    SyncItem(kind = kind, uuid = uuid, tripUuid = tripUuid, updatedAt = deletedAt, data = null)
