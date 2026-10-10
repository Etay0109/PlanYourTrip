package com.example.planyourtrip.data.sync

import android.content.SharedPreferences

/**
 * Sync facts remembered on this device, per account: the one-time move of the account's legacy cloud copy to the
 * shared-trips layout ([LegacyMigration]) and the result of its last full sync ([FullSyncRecord]). Tests use a fake.
 */
interface SyncFlags {
    fun isSharedTripsMigrated(uid: String): Boolean

    fun markSharedTripsMigrated(uid: String)

    /** The account's last full sync on this device; null = none finished yet. */
    fun lastFullSync(uid: String): FullSyncRecord?

    fun recordFullSync(uid: String, record: FullSyncRecord)
}

/**
 * How the last full sync of an account ended. [lastSuccessAt] = epoch millis of the last one that succeeded (null =
 * never on this device), kept when a later one fails.
 */
data class FullSyncRecord(val succeeded: Boolean, val lastSuccessAt: Long?)

/**
 * SharedPreferences "sync": `shared_trips_migrated_<uid>`, `full_sync_ok_<uid>` and `full_sync_success_at_<uid>`.
 */
class SharedPreferencesSyncFlags(private val prefs: SharedPreferences) : SyncFlags {
    override fun isSharedTripsMigrated(uid: String): Boolean = prefs.getBoolean(migratedKey(uid), false)

    override fun markSharedTripsMigrated(uid: String) {
        prefs.edit().putBoolean(migratedKey(uid), true).apply()
    }

    override fun lastFullSync(uid: String): FullSyncRecord? {
        if (!prefs.contains(okKey(uid))) return null
        val successAt = prefs.getLong(successKey(uid), 0L).takeIf { it > 0 }
        return FullSyncRecord(prefs.getBoolean(okKey(uid), false), successAt)
    }

    override fun recordFullSync(uid: String, record: FullSyncRecord) {
        val editor = prefs.edit().putBoolean(okKey(uid), record.succeeded)
        if (record.lastSuccessAt != null) editor.putLong(successKey(uid), record.lastSuccessAt) else editor.remove(successKey(uid))
        editor.apply()
    }

    private fun migratedKey(uid: String) = "shared_trips_migrated_$uid"

    private fun okKey(uid: String) = "full_sync_ok_$uid"

    private fun successKey(uid: String) = "full_sync_success_at_$uid"
}
