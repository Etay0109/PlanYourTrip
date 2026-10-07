package com.example.planyourtrip.data.sync

import android.content.SharedPreferences

/**
 * One-time sync jobs remembered on this device, per account. Only the removal of the old place documents
 * ([CloudStore.deleteLegacyPlaces]) for now. Tests use a fake.
 */
interface SyncFlags {
    fun isPlacesCleanupDone(uid: String): Boolean

    fun markPlacesCleanupDone(uid: String)
}

/** SharedPreferences "sync", key `places_cleanup_<uid>`. */
class SharedPreferencesSyncFlags(private val prefs: SharedPreferences) : SyncFlags {
    override fun isPlacesCleanupDone(uid: String): Boolean = prefs.getBoolean(key(uid), false)

    override fun markPlacesCleanupDone(uid: String) {
        prefs.edit().putBoolean(key(uid), true).apply()
    }

    private fun key(uid: String) = "places_cleanup_$uid"
}
