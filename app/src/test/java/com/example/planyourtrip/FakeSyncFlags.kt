package com.example.planyourtrip

import com.example.planyourtrip.data.sync.FullSyncRecord
import com.example.planyourtrip.data.sync.SyncFlags

/**
 * In-memory [SyncFlags]: the accounts whose legacy cloud copy was moved to shared trips on this device, and each
 * account's last full sync ([fullSyncs]).
 */
class FakeSyncFlags : SyncFlags {
    val sharedTripsMigrated = mutableSetOf<String>()
    val fullSyncs = mutableMapOf<String, FullSyncRecord>()

    override fun isSharedTripsMigrated(uid: String): Boolean = uid in sharedTripsMigrated

    override fun markSharedTripsMigrated(uid: String) {
        sharedTripsMigrated += uid
    }

    override fun lastFullSync(uid: String): FullSyncRecord? = fullSyncs[uid]

    override fun recordFullSync(uid: String, record: FullSyncRecord) {
        fullSyncs[uid] = record
    }
}
