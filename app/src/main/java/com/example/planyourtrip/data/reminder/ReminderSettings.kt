package com.example.planyourtrip.data.reminder

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate

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

    /** The reminders already shown on this device ([shownKey]s): each one is shown at most once. */
    fun shown(): Set<String>

    /** Remembers [key] as shown, written before this returns (the reminder must never show twice). */
    fun markShown(key: String)

    /** Forgets shown reminders of trips that started before [today] (their key can never be asked for again). */
    fun pruneShown(today: LocalDate)

    /** The one-time "Get trip reminders on time" battery screen was shown (never shown again). */
    var batteryTipShown: Boolean
}

/** [ReminderSettings] in the prefs "reminders". */
class SharedPreferencesReminderSettings(private val prefs: SharedPreferences) : ReminderSettings {
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    override fun setEnabled(on: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, on) }
        _enabled.value = on
    }

    override var permissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_PERMISSION_ASKED, value) }

    /** Stored as "uuid|uid|epochMillis" (uuids and uids never contain "|"); unreadable entries are skipped. */
    override fun scheduled(): Map<String, Reminder> =
        prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty().mapNotNull { entry ->
            val parts = entry.split('|')
            val millis = parts.getOrNull(2)?.toLongOrNull() ?: return@mapNotNull null
            if (parts.size != 3) null else parts[0] to Reminder(parts[1], Instant.ofEpochMilli(millis))
        }.toMap()

    override fun setScheduled(reminders: Map<String, Reminder>) {
        val entries = reminders.map { (uuid, r) -> "$uuid|${r.uid}|${r.at.toEpochMilli()}" }.toSet()
        prefs.edit { putStringSet(KEY_SCHEDULED, entries) }
    }

    override fun shown(): Set<String> = prefs.getStringSet(KEY_SHOWN, emptySet()).orEmpty().toSet()

    override fun markShown(key: String) {
        // Written right away (commit): this runs in the alarm's receiver, and the process may end just after it, so
        // a reminder is never shown twice.
        prefs.edit(commit = true) { putStringSet(KEY_SHOWN, shown() + key) }
    }

    override fun pruneShown(today: LocalDate) {
        val shown = shown()
        val kept = shown.filterTo(mutableSetOf()) { key -> shownKeyStart(key)?.isBefore(today) == false }
        if (kept != shown) prefs.edit { putStringSet(KEY_SHOWN, kept) }
    }

    override var batteryTipShown: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_TIP_SHOWN, false)
        set(value) = prefs.edit { putBoolean(KEY_BATTERY_TIP_SHOWN, value) }

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_SHOWN = "shown"
        const val KEY_BATTERY_TIP_SHOWN = "battery_tip_shown"
        const val KEY_PERMISSION_ASKED = "permission_asked"
        const val KEY_SCHEDULED = "scheduled"
    }
}
