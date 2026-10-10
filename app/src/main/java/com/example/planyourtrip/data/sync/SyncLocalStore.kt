package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.trip.TripMember
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

    /** Each of the owner's trips on this device: uuid → the trip's cloud owner (blank when unknown). */
    suspend fun tripOwners(ownerId: String): Map<String, String>

    /**
     * Removes trips the account lost (left, removed, or deleted by their owner) in one transaction: the trips with
     * all their items, **including changes not uploaded yet**, and every tombstone of those trips. Documents (this
     * device only) are not touched here.
     */
    suspend fun removeTrips(ownerId: String, tripUuids: Set<String>)

    /** Replaces the member documents of each trip in [members] (trip uuid → members); trips not here are skipped. */
    suspend fun replaceMembers(ownerId: String, members: Map<String, List<TripMember>>)

    /** Removes all the owner's data from this device (after sign out). */
    suspend fun deleteAllFor(ownerId: String)
}
