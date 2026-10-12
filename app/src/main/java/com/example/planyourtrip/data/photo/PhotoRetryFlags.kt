package com.example.planyourtrip.data.photo

import android.content.SharedPreferences

/**
 * One-time clean-ups of the stored photo lookups, remembered on this device. Each version clears every stored
 * "nothing found" once, so destinations that found nothing under older lookup rules are looked up again right away
 * instead of a week later. Bump [CURRENT_VERSION] when the rules change enough to deserve a retry.
 */
interface PhotoRetryFlags {
    /** The last clean-up version done on this device (0 = none). */
    fun clearedVersion(): Int

    fun markCleared(version: Int)

    companion object {
        /** 1 = the province fallback and the collage rules (2026-10). */
        const val CURRENT_VERSION = 1
    }

    /** Nothing to clean up (previews and tests that don't care). */
    object Done : PhotoRetryFlags {
        override fun clearedVersion(): Int = Int.MAX_VALUE
        override fun markCleared(version: Int) = Unit
    }
}

/** SharedPreferences "photos": `not_found_cleared_version`. */
class SharedPreferencesPhotoRetryFlags(private val prefs: SharedPreferences) : PhotoRetryFlags {
    override fun clearedVersion(): Int = prefs.getInt(KEY, 0)

    override fun markCleared(version: Int) {
        prefs.edit().putInt(KEY, version).apply()
    }

    private companion object {
        const val KEY = "not_found_cleared_version"
    }
}
