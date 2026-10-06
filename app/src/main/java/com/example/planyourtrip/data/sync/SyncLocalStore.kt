package com.example.planyourtrip.data.sync

import kotlinx.coroutines.flow.Flow

/** This device's side of the sync, always for one owner. The real implementation is [RoomSyncLocalStore]. */
interface SyncLocalStore {
    /** The owner's items and local tombstones; with [pendingOnly], only what still has to be uploaded. */
    suspend fun snapshot(ownerId: String, pendingOnly: Boolean): List<LocalItem>

    /**
     * Applies a [MergePlan] in one transaction. A saved cloud version never replaces a newer local change, and a
     * row is deleted only while it still has the `updatedAt` the plan saw.
     */
    suspend fun apply(ownerId: String, plan: MergePlan)

    /** Clears the pending flag of an uploaded item (or drops its tombstone), unless it changed again since. */
    suspend fun markSynced(ownerId: String, item: SyncItem)

    /** Number of changes still waiting to upload (pending rows + tombstones). */
    fun pendingCount(ownerId: String): Flow<Int>

    /** Removes all the owner's data from this device (after sign out). */
    suspend fun deleteAllFor(ownerId: String)
}
