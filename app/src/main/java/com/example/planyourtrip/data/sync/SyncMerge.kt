package com.example.planyourtrip.data.sync

/**
 * What one sync does after comparing this device with the cloud (see [planMerge]).
 * Applying it locally is guarded again by `updatedAt`, so a change made while the sync runs is never overwritten.
 */
data class MergePlan(
    /** Cloud versions to store on this device (insert or replace), trips before their expenses/activities. */
    val saveLocally: List<SyncItem> = emptyList(),
    /** Local versions to remove from this device (deleted elsewhere or removed from the cloud). */
    val deleteLocally: List<SyncItem> = emptyList(),
    /** Local versions to write to the cloud: changed items and tombstones of deletes. */
    val upload: List<SyncItem> = emptyList(),
    /** Local tombstones that are no longer needed (the item is gone everywhere, or the cloud edit won). */
    val dropTombstones: List<SyncItem> = emptyList(),
)

/**
 * Merges this device's items of one account with the cloud's, item by item (by uuid).
 * Pure: the same function serves the full sync (all items on both sides) and the quick upload after a local
 * change (only the pending items and their cloud versions).
 *
 * | local                | cloud     | result                                                              |
 * |----------------------|-----------|---------------------------------------------------------------------|
 * | live, pending        | missing   | upload (created here, or the first upload of existing data)         |
 * | live, not pending    | missing   | delete here (it was removed from the cloud)                         |
 * | missing              | live      | save here                                                           |
 * | live                 | live      | newer `updatedAt` wins, a tie goes to the cloud                     |
 * | live                 | tombstone | the newer of edit and delete wins (tie: delete)                     |
 * | tombstone            | live      | the newer of delete and edit wins (tie: the cloud's edit)           |
 * | tombstone            | missing / tombstone | drop the local tombstone                                  |
 *
 * Expenses and activities of a trip that ends up deleted are deleted here and neither saved nor uploaded.
 */
fun planMerge(local: List<LocalItem>, cloud: List<SyncItem>): MergePlan {
    val localByUuid = local.associateBy { it.item.uuid }
    val cloudByUuid = cloud.associateBy { it.uuid }
    val uuids = (localByUuid.keys + cloudByUuid.keys)

    val decisions = uuids.map { uuid -> decide(localByUuid[uuid], cloudByUuid[uuid]) }

    // Trips that are deleted after this merge (only trips this merge knows about).
    val deadTrips = decisions
        .filter { it.kind == SyncKind.TRIP && it.winner?.isDeleted != false }
        .map { it.uuid }
        .toSet()

    val save = mutableListOf<SyncItem>()
    val delete = mutableListOf<SyncItem>()
    val upload = mutableListOf<SyncItem>()
    val drop = mutableListOf<SyncItem>()
    for (d in decisions) {
        val orphan = d.kind != SyncKind.TRIP && d.tripUuid in deadTrips
        if (orphan) {
            // The trip's delete covers its children everywhere (the cloud tombstones them with the trip).
            val localItem = localByUuid[d.uuid]?.item
            if (localItem != null) {
                if (localItem.isDeleted) drop += localItem else delete += localItem
            }
            continue
        }
        d.save?.let { save += it }
        d.delete?.let { delete += it }
        d.upload?.let { upload += it }
        d.drop?.let { drop += it }
    }
    return MergePlan(
        saveLocally = save.sortedWith(saveOrder),
        deleteLocally = delete,
        upload = upload,
        dropTombstones = drop,
    )
}

/**
 * Trips before their children; checklist items in creation order (ties by uuid, the same on every device), so the
 * rows they become here are inserted in list order and `ORDER BY created_at, id` matches the other devices.
 */
private val saveOrder: Comparator<SyncItem> = compareBy<SyncItem>(
    { it.kind.ordinal },
    { (it.data as? ItemData.ChecklistData)?.createdAt ?: 0L },
    { if (it.kind == SyncKind.CHECKLIST) it.uuid else "" },
)

private class Decision(
    val uuid: String,
    val kind: SyncKind,
    val tripUuid: String?,
    /** The version that holds after the merge; null or a tombstone = deleted. */
    val winner: SyncItem?,
    val save: SyncItem? = null,
    val delete: SyncItem? = null,
    val upload: SyncItem? = null,
    val drop: SyncItem? = null,
)

private fun decide(local: LocalItem?, cloud: SyncItem?): Decision {
    val any = checkNotNull(local?.item ?: cloud)
    fun decision(
        winner: SyncItem?,
        save: SyncItem? = null,
        delete: SyncItem? = null,
        upload: SyncItem? = null,
        drop: SyncItem? = null,
    ) = Decision(any.uuid, any.kind, any.tripUuid, winner, save, delete, upload, drop)

    val mine = local?.item
    return when {
        mine == null -> if (cloud!!.isDeleted) decision(winner = cloud) else decision(winner = cloud, save = cloud)

        mine.isDeleted -> when {
            cloud == null || cloud.isDeleted -> decision(winner = null, drop = mine)
            mine.updatedAt > cloud.updatedAt -> decision(winner = mine, upload = mine)
            else -> decision(winner = cloud, save = cloud, drop = mine)
        }

        cloud == null ->
            if (local.pending) decision(winner = mine, upload = mine) else decision(winner = null, delete = mine)

        cloud.isDeleted ->
            if (mine.updatedAt > cloud.updatedAt) decision(winner = mine, upload = mine)
            else decision(winner = cloud, delete = mine)

        mine.updatedAt > cloud.updatedAt -> decision(winner = mine, upload = mine)

        // The cloud is newer or equally new. Nothing to do when this device already has exactly that version.
        cloud == mine && !local.pending -> decision(winner = cloud)

        else -> decision(winner = cloud, save = cloud)
    }
}
