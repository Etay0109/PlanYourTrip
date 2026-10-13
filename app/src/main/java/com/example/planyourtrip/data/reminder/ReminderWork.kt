package com.example.planyourtrip.data.reminder

import java.time.Instant

/**
 * Where reminders are scheduled (an alarm + a WorkManager backup in the app, a fake in tests). One reminder per trip
 * uuid. Firing it only asks [ReminderDelivery], which shows each reminder at most once.
 */
interface ReminderWork {
    /** Schedules (or replaces) the reminder of [tripUuid] for [uid] at [at]. */
    fun schedule(tripUuid: String, uid: String, at: Instant)

    fun cancel(tripUuid: String)

    /** Cancels every reminder ([tripUuids] = the ones this device scheduled, for what can't be found by tag). */
    fun cancelAll(tripUuids: Collection<String>)

    /** The trips whose WorkManager request is really waiting (or running) now, read from WorkManager itself. */
    suspend fun pendingUuids(): Set<String>
}
