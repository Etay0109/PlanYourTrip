package com.example.planyourtrip

import com.example.planyourtrip.data.sync.SyncFlags

/** In-memory [SyncFlags]: the accounts whose old place documents were removed. */
class FakeSyncFlags : SyncFlags {
    val placesCleanupDone = mutableSetOf<String>()

    override fun isPlacesCleanupDone(uid: String): Boolean = uid in placesCleanupDone

    override fun markPlacesCleanupDone(uid: String) {
        placesCleanupDone += uid
    }
}
