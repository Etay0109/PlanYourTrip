package com.example.planyourtrip.data.reminder

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/** The reminders' settings and bookkeeping, on this device only (never synced, not per account). */
interface ReminderSettings {
    /** The profile menu's "Trip reminders" switch (on by default). */
    val enabled: StateFlow<Boolean>

    fun setEnabled(on: Boolean)

    /** The notification permission was asked for once after adding a trip (never asked that way again). */
    var permissionAsked: Boolean

    /** The reminders this device scheduled, by trip uuid (checked against WorkManager at every app start). */
    fun scheduled(): Map<String, Reminder>

    fun setScheduled(reminders: Map<String, Reminder>)
}

/** [ReminderSettings] in the prefs "reminders". */
class SharedPreferencesReminderSettings(private val prefs: SharedPreferences) : ReminderSettings {
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    override fun setEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
    }

    override var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()

    /** Stored as "uuid|uid|epochMillis" (uuids and uids never contain "|"); unreadable entries are skipped. */
    override fun scheduled(): Map<String, Reminder> =
        prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().mapNotNull { entry ->
            val parts = entry.split('|')
            val millis = parts.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
            if (parts.size != 3) null else parts[0] to Reminder(parts[1], Instant.ofEpochMilli(millis))
        }.toMap()

    override fun setScheduled(reminders: Map<String, Reminder>) {
        val entries = reminders.map { (uuid, r) -> "$uuid|${r.uid}|${r.at.toEpochMilli()}" }.toSet()
        prefs.edit().putStringSet(KEY_SCHEDULED, entries).apply()
    }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_PERMISSION_ASKED = "permission_asked"
        const val KEY_SCHEDULED = "scheduled"
    }
}
