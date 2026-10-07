package com.example.planyourtrip.data.sync

import androidx.room.withTransaction
import com.example.planyourtrip.data.trip.ActivityEntity
import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.AppDatabase
import com.example.planyourtrip.data.trip.ChecklistItemEntity
import com.example.planyourtrip.data.trip.ExpenseEntity
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
        }
    }

    override fun pendingCount(ownerId: String): Flow<Int> = dao.pendingCount(ownerId)

    override suspend fun deleteAllFor(ownerId: String) = db.withTransaction {
        // Expenses, activities and checklist items go with their trips (foreign key CASCADE). Documents are not
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
                val trip = TripEntity(
                    id = existing?.id ?: 0,
                    ownerId = ownerId,
                    destination = data.destination,
                    startDate = data.startDate,
                    endDate = data.endDate,
                    color = data.color,
                    budgetAgorot = data.budgetAgorot,
                    currencyCode = data.currencyCode,
                    uuid = item.uuid,
                    updatedAt = item.updatedAt,
                    pendingSync = false,
                )
                if (existing == null) dao.insertTrip(trip) else dao.updateTrip(trip)
            }
            is ItemData.ExpenseData -> {
                val tripId = ownedTripId(ownerId, item) ?: return
                val existing = dao.expenseByUuid(item.uuid)
                if (existing != null && existing.updatedAt > item.updatedAt) return
                val expense = ExpenseEntity(
                    id = existing?.id ?: 0,
                    tripId = tripId,
                    name = data.name,
                    amountAgorot = data.amountAgorot,
                    category = data.category,
                    date = data.date,
                    originalAmount = data.original?.amount,
                    originalCurrency = data.original?.currencyCode,
                    exchangeRate = data.original?.ilsPerUnit,
                    rateDate = data.original?.rateDate,
                    uuid = item.uuid,
                    updatedAt = item.updatedAt,
                    pendingSync = false,
                )
                if (existing == null) dao.insertExpense(expense) else dao.updateExpense(expense)
            }
            is ItemData.ActivityData -> {
                val tripId = ownedTripId(ownerId, item) ?: return
                val existing = dao.activityByUuid(item.uuid)
                if (existing != null && existing.updatedAt > item.updatedAt) return
                val input = data.input
                val activity = ActivityEntity(
                    id = existing?.id ?: 0,
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
                if (existing == null) dao.insertActivity(activity) else dao.updateActivity(activity)
            }
            is ItemData.ChecklistData -> {
                val tripId = ownedTripId(ownerId, item) ?: return
                val existing = dao.checklistItemByUuid(item.uuid)
                if (existing != null && existing.updatedAt > item.updatedAt) return
                val checklistItem = ChecklistItemEntity(
                    id = existing?.id ?: 0,
                    tripId = tripId,
                    list = data.list,
                    text = data.text,
                    checked = data.checked,
                    dueDate = data.dueDate,
                    createdAt = data.createdAt,
                    uuid = item.uuid,
                    updatedAt = item.updatedAt,
                    pendingSync = false,
                )
                if (existing == null) dao.insertChecklistItem(checklistItem) else dao.updateChecklistItem(checklistItem)
            }
        }
    }

    /** Local id of the item's trip, or null when the trip isn't on this device for this owner. */
    private suspend fun ownedTripId(ownerId: String, item: SyncItem): Long? {
        val trip = dao.tripByUuid(item.tripUuid ?: return null) ?: return null
        return trip.id.takeIf { trip.ownerId == ownerId }
    }
}

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
        data = ItemData.ExpenseData(domain.name, domain.amountAgorot, domain.category, domain.date, domain.original),
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

private fun DeletionEntity.toSyncItem() =
    SyncItem(kind = kind, uuid = uuid, tripUuid = tripUuid, updatedAt = deletedAt, data = null)
