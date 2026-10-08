package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ForeignAmount
import com.example.planyourtrip.data.trip.ME
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.TripColor
import java.time.LocalDate

/**
 * What kind of item a [SyncItem] is. Stored by name in the `deletions` table, so entries may be reordered; the
 * order is the order in which cloud versions are saved (trips first).
 */
enum class SyncKind { TRIP, TRAVELER, EXPENSE, ACTIVITY, CHECKLIST, SETTLEMENT }

/**
 * One trip or child item (traveler, expense, activity, checklist item, settlement) as the sync layer sees it, identical on every device and in the cloud.
 * [data] is null for a deleted item (a tombstone); [updatedAt] is then the time of the delete.
 */
data class SyncItem(
    val kind: SyncKind,
    val uuid: String,
    /** The trip's uuid for a child item; null for a trip. */
    val tripUuid: String?,
    /** Epoch millis of the last change (or of the delete). The newer change wins. */
    val updatedAt: Long,
    val data: ItemData?,
) {
    val isDeleted: Boolean get() = data == null
}

/** The synced fields of an item. Local ids, owner and the pending flag are never synced. */
sealed interface ItemData {
    data class TripData(
        val destination: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val color: TripColor,
        val budgetAgorot: Long?,
        val currencyCode: String?,
    ) : ItemData

    data class ExpenseData(
        val name: String,
        val amountAgorot: Long,
        val category: ExpenseCategory,
        val date: LocalDate,
        val original: ForeignAmount?,
        /** Traveler key of the payer ("me" or a traveler uuid). */
        val paidBy: String = ME,
        /** Traveler keys sharing the expense equally; never empty. */
        val splitBetween: List<String> = listOf(ME),
    ) : ItemData

    data class ActivityData(val input: ActivityInput) : ItemData

    data class ChecklistData(
        val list: ChecklistKind,
        val text: String,
        val checked: Boolean,
        val dueDate: LocalDate?,
        /** Epoch millis of when the item was added (the list order on every device). */
        val createdAt: Long,
    ) : ItemData

    data class TravelerData(
        val name: String,
        val color: TravelerColor,
        /** Epoch millis of when the traveler was added (the list order on every device). */
        val createdAt: Long,
    ) : ItemData

    data class SettlementData(
        val fromKey: String,
        val toKey: String,
        val amountAgorot: Long,
        val date: LocalDate,
        val createdAt: Long,
    ) : ItemData
}

/** An item on this device: [pending] = changed here and not uploaded yet. Local tombstones are always pending. */
data class LocalItem(val item: SyncItem, val pending: Boolean)

/** "All changes saved" / "Syncing…" / "Offline – changes will sync later" in the Home profile menu. */
enum class SyncStatus { SAVED, SYNCING, OFFLINE }
