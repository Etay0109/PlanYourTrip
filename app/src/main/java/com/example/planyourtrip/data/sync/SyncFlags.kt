package com.example.planyourtrip.data.sync

import android.content.SharedPreferences

/**
 * One-time sync jobs remembered on this device, per account: the move of the account's legacy cloud copy to the
 * shared-trips layout ([LegacyMigration]). Tests use a fake.
 */
interface SyncFlags {
    fun isSharedTripsMigrated(uid: String): Boolean

    fun markSharedTripsMigrated(uid: String)
}

/** SharedPreferences "sync", key `shared_trips_migrated_<uid>`. */
class SharedPreferencesSyncFlags(private val prefs: SharedPreferences) : SyncFlags {
    override fun isSharedTripsMigrated(uid: String): Boolean = prefs.getBoolean(key(uid), false)

    override fun markSharedTripsMigrated(uid: String) {
        prefs.edit().putBoolean(key(uid), true).apply()
    }

    private fun key(uid: String) = "shared_trips_migrated_$uid"
}
