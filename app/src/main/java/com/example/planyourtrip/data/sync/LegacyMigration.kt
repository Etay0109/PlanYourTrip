package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.LEGACY_ME_KEY

// The move from the legacy cloud layout (users/{uid}/trips/…, one account per trip, the owner's key "me") to shared
// trips (trips/{tripUuid}/…, members by uid). Pure, unit-tested; SyncManager runs it once per account:
//  1. read the legacy copy and convert it ([convertLegacy]),
//  2. copy what the new layout lacks or has in an older version ([planLegacyCopy]),
//  3. read the new layout again and check that nothing is left to copy ([isLegacyCopyComplete]),
//  4. only then mark the legacy copy migrated and delete it.
// Every step can be repeated: an interrupted migration starts again from step 1 and never copies an older version
// over a newer one.

/**
 * The legacy items of [uid] in the new layout: the key "me" becomes [uid] (payer, split, settlements), each trip gets
 * [uid] as owner and only member, Packing items become personal [SyncKind.PACKING] items. A legacy checklist
 * tombstone doesn't say which list it was in, so it becomes a tombstone of both kinds.
 */
fun convertLegacy(items: List<SyncItem>, uid: String): List<SyncItem> = items.flatMap { item ->
    fun key(k: String) = if (k == LEGACY_ME_KEY) uid else k
    when (val data = item.data) {
        null -> if (item.kind == SyncKind.CHECKLIST) listOf(item, item.copy(kind = SyncKind.PACKING)) else listOf(item)
        is ItemData.TripData -> listOf(item.copy(data = data.copy(ownerId = uid, memberIds = listOf(uid))))
        is ItemData.ExpenseData -> listOf(
            item.copy(data = data.copy(paidBy = key(data.paidBy), splitBetween = data.splitBetween.map(::key).distinct()))
        )
        is ItemData.SettlementData -> listOf(item.copy(data = data.copy(fromKey = key(data.fromKey), toKey = key(data.toKey))))
        is ItemData.ChecklistData ->
            listOf(if (data.list == ChecklistKind.PACKING) item.copy(kind = SyncKind.PACKING) else item)
        else -> listOf(item)
    }
}

/**
 * The converted [legacy] items to write to the new layout, trips first: those [current] (the new layout as the
 * account sees it) doesn't have, or has in an older version. A tombstone of an item the new layout never had is
 * copied too (so a pending edit on another phone can't bring a deleted item back), except children of trips that
 * end up deleted: the trip's tombstone covers them, and the rules accept no live child of a deleted trip.
 */
fun planLegacyCopy(legacy: List<SyncItem>, current: List<SyncItem>): List<SyncItem> {
    val currentByKey = current.associateBy { it.kind to it.uuid }
    fun isNewer(item: SyncItem): Boolean {
        val existing = currentByKey[item.kind to item.uuid] ?: return true
        return item.updatedAt > existing.updatedAt
    }
    // The version of each trip after the copy: the legacy one where it is newer, else the current one.
    val tripAfter = (current + legacy).filter { it.kind == SyncKind.TRIP }
        .groupBy { it.uuid }
        .mapValues { (_, versions) -> versions.maxWith(compareBy<SyncItem> { it.updatedAt }.thenBy { it in current }) }
    return legacy
        .filter { item ->
            if (item.kind == SyncKind.TRIP) return@filter isNewer(item)
            val trip = tripAfter[item.tripUuid]
            trip != null && !trip.isDeleted && isNewer(item)
        }
        .sortedBy { it.kind.ordinal }
}

/** True when the new layout holds every converted [legacy] item in the same or a newer version. */
fun isLegacyCopyComplete(legacy: List<SyncItem>, current: List<SyncItem>): Boolean =
    planLegacyCopy(legacy, current).isEmpty()
