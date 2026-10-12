package com.example.planyourtrip.data.reminder

import java.time.Instant

/** Where reminders are scheduled (WorkManager in the app, a fake in tests). One reminder per trip uuid. */
interface ReminderWork {
    /** Schedules (or replaces) the reminder of [tripUuid] for [uid] at [at]. */
    fun schedule(tripUuid: String, uid: String, at: Instant)

    fun cancel(tripUuid: String)

    fun cancelAll()

    /** The trips whose reminder is really waiting (or running) now, read from the scheduler itself. */
    suspend fun pendingUuids(): Set<String>
}
