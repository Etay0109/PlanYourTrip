package com.example.planyourtrip

import com.example.planyourtrip.data.reminder.Reminder
import com.example.planyourtrip.data.reminder.ReminderPrompt
import com.example.planyourtrip.data.reminder.ReminderSettings
import com.example.planyourtrip.data.reminder.ReminderWork
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate

/** In-memory [ReminderSettings]. */
class FakeReminderSettings(
    enabled: Boolean = true,
    var saved: Map<String, Reminder> = emptyMap(),
) : ReminderSettings {
    override val enabled = MutableStateFlow(enabled)

    override fun setEnabled(on: Boolean) {
        enabled.value = on
    }

    override var permissionAsked: Boolean = false

    override fun scheduled(): Map<String, Reminder> = saved

    override fun setScheduled(reminders: Map<String, Reminder>) {
        saved = reminders
    }
}

/** In-memory [ReminderWork]: what is pending, and every call (`schedule:<uuid>`, `cancel:<uuid>`, `cancelAll`). */
class FakeReminderWork(initial: Map<String, Reminder> = emptyMap()) : ReminderWork {
    val pending = initial.toMutableMap()
    val calls = mutableListOf<String>()

    override fun schedule(tripUuid: String, uid: String, at: Instant) {
        calls += "schedule:$tripUuid"
        pending[tripUuid] = Reminder(uid, at)
    }

    override fun cancel(tripUuid: String) {
        calls += "cancel:$tripUuid"
        pending.remove(tripUuid)
    }

    override fun cancelAll() {
        calls += "cancelAll"
        pending.clear()
    }

    /** WorkManager lost a request (e.g. app data partly restored): it is no longer pending. */
    fun drop(tripUuid: String) {
        pending.remove(tripUuid)
    }

    override suspend fun pendingUuids(): Set<String> = pending.keys.toSet()
}

/** Records the trips the prompt was told about. */
class FakeReminderPrompt : ReminderPrompt {
    val added = mutableListOf<LocalDate>()

    override fun onTripAdded(startDate: LocalDate) {
        added += startDate
    }
}
