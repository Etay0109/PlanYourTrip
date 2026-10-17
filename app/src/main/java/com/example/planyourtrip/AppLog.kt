package com.example.planyourtrip

import android.util.Log

/**
 * The app's only logger. Debug builds write to logcat (`adb logcat -s CloudSync` etc.); release builds log nothing,
 * so no user content (destinations, assistant answers, document paths) ever ends up in a release device's log.
 * [LOGGING_ENABLED] comes from `src/debug` / `src/release` (LogConfig.kt).
 */
object AppLog {
    fun i(tag: String, message: String) {
        if (LOGGING_ENABLED) Log.i(tag, message)
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (LOGGING_ENABLED) Log.w(tag, message, error)
    }
}
