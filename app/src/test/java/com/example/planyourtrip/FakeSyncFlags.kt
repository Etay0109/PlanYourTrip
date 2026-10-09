package com.example.planyourtrip

import com.example.planyourtrip.data.sync.SyncFlags

/** In-memory [SyncFlags]: the accounts whose legacy cloud copy was moved to shared trips on this device. */
class FakeSyncFlags : SyncFlags {
    val sharedTripsMigrated = mutableSetOf<String>()

    override fun isSharedTripsMigrated(uid: String): Boolean = uid in sharedTripsMigrated

    override fun markSharedTripsMigrated(uid: String) {
        sharedTripsMigrated += uid
    }
}
