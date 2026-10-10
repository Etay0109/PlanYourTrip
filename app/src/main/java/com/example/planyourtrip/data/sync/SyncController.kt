package com.example.planyourtrip.data.sync

import kotlinx.coroutines.flow.StateFlow

/** What the UI may see and do with the sync (implemented by [SyncManager], faked in ViewModel tests). */
interface SyncController {
    /** Sync state of the signed-in account. */
    val status: StateFlow<SyncStatus>

    /** Epoch millis of the signed-in account's last successful full sync on this device; null = never. */
    val lastSyncedAt: StateFlow<Long?>

    /** A full sync now (pull to refresh). False when offline or the sync failed; the data on the device stays. */
    suspend fun refresh(): Boolean

    /**
     * Signs out. Without [force]: a full sync runs first (it uploads pending changes); the account's data is removed
     * from this device only when it is safely in the cloud: the legacy migration finished for the account, the last
     * full sync succeeded and nothing is pending. Otherwise nothing happens and [SignOutResult.NEEDS_CONFIRMATION]
     * (changes still pending) or [SignOutResult.NOT_CONFIRMED] (nothing pending, but the cloud copy isn't confirmed)
     * is returned. With [force]: signs out and keeps the account's data on the device (hidden from other accounts),
     * so it syncs the next time that account signs in here.
     */
    suspend fun signOut(force: Boolean): SignOutResult

    /**
     * Leaves the shared trip [tripUuid] (not as its owner): one cloud write that also deletes my budget and Packing
     * list, then the trip and its documents are removed from this device, even with changes not uploaded yet. False
     * when offline, for the owner, or when the write failed (nothing changed then).
     */
    suspend fun leaveTrip(tripUuid: String): Boolean

    /**
     * The owner removes [memberUid] from trip [tripUuid], then a full sync updates this device. False when offline or
     * the write failed (nothing changed then).
     */
    suspend fun removeMember(tripUuid: String, memberUid: String): Boolean
}

enum class SignOutResult {
    SIGNED_OUT,

    /** Changes still wait to upload: ask before signing out ("Sign out anyway" keeps them on this device). */
    NEEDS_CONFIRMATION,

    /** Nothing is pending, but no full sync confirmed that the cloud holds this device's data: ask too. */
    NOT_CONFIRMED,
}

/**
 * Called after every successful full sync, when this device holds every trip of the account: the documents (kept
 * on this device only) of trips that are gone are removed. [forgetTrip] when a trip is removed because the account
 * lost it.
 */
fun interface TripCleanup {
    suspend fun afterFullSync(ownerId: String)

    /** The account lost trip [tripUuid] (left, removed or deleted by its owner): its documents go too. */
    suspend fun forgetTrip(ownerId: String, tripUuid: String) {}

    companion object {
        val None = TripCleanup {}
    }
}

/** Called by the Room repositories after every local create, edit and delete. */
fun interface LocalChangeListener {
    fun onLocalChange()

    companion object {
        val None = LocalChangeListener {}
    }
}
