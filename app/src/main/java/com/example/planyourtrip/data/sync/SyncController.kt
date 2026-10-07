package com.example.planyourtrip.data.sync

import kotlinx.coroutines.flow.StateFlow

/** What the UI may see and do with the sync (implemented by [SyncManager], faked in ViewModel tests). */
interface SyncController {
    /** Sync state of the signed-in account. */
    val status: StateFlow<SyncStatus>

    /**
     * Signs out. Without [force]: pending changes are uploaded first; when that worked (or nothing was pending)
     * the account's data is removed from this device, otherwise nothing happens and [SignOutResult.NEEDS_CONFIRMATION]
     * is returned. With [force]: signs out and keeps the account's data on the device (hidden from other accounts),
     * so it uploads the next time that account signs in here.
     */
    suspend fun signOut(force: Boolean): SignOutResult
}

enum class SignOutResult { SIGNED_OUT, NEEDS_CONFIRMATION }

/**
 * Called after every successful full sync, when this device holds every trip of the account: the documents (kept
 * on this device only) of trips that are gone are removed.
 */
fun interface TripCleanup {
    suspend fun afterFullSync(ownerId: String)

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
