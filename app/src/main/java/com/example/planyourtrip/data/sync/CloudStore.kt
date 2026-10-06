package com.example.planyourtrip.data.sync

/**
 * The cloud copy of one account's items under users/{uid}/trips/… . ViewModels never see it; only [SyncManager]
 * uses it. The real implementation is [FirestoreCloudStore]; tests use a fake. Every failure (offline, timeout,
 * permission) throws [CloudUnavailableException].
 */
interface CloudStore {
    /** Every trip, expense and activity of [uid], tombstones included. */
    suspend fun fetchAll(uid: String): List<SyncItem>

    /** The cloud versions of [items] (same kind, uuid and trip); items missing in the cloud are left out. */
    suspend fun fetch(uid: String, items: List<SyncItem>): List<SyncItem>

    /**
     * Writes [items] as they are: live items replace the document, deleted ones become tombstones. A trip
     * tombstone also turns its expenses and activities into tombstones.
     */
    suspend fun write(uid: String, items: List<SyncItem>)
}

class CloudUnavailableException(cause: Throwable? = null) : Exception(cause)
