package com.example.planyourtrip.data.reminder

import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.trip.Trip
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import java.time.LocalDate

/** Logcat tag of everything about reminders (`adb logcat -s TripReminder`). Trip uuids only, never destinations. */
const val REMINDER_LOG_TAG = "TripReminder"

/** Where a reminder is shown: the Android notification ([TripReminderNotifier]), a fake in tests. */
interface ReminderPoster {
    /** Notifications may be shown (Android 13+ permission granted and not turned off for the app). */
    fun canPost(): Boolean

    fun post(trip: Trip, uid: String, message: ReminderMessage)
}

/** What [ReminderDelivery.deliver] did. */
enum class DeliveryResult {
    POSTED,

    /** Shown already (by the alarm, the WorkManager backup or the start-up check): never twice. */
    ALREADY_SHOWN,

    /** After midnight: the trip has started, the reminder is never shown. */
    TOO_LATE,

    /** Not wanted now: signed out, another account, trip deleted or moved, switch off, notifications refused. */
    NOT_WANTED,
}

/**
 * Shows a trip's "trip starts tomorrow" reminder **at most once**, only the evening before (from its 18:00 until
 * midnight; [shouldShowReminder]), whoever asks: the alarm, the WorkManager backup, the scheduler's check at app start,
 * or the debug command. One at a time ([Mutex]): they can fire within milliseconds of each other in the one process.
 * The "Before the trip" list is counted now, not when the reminder was scheduled. A reminder that isn't wanted at the
 * moment (e.g. notifications refused) is not remembered, so it can still be shown later that evening.
 */
class ReminderDelivery(
    private val authRepository: AuthRepository,
    private val trips: ReminderTrips,
    private val settings: ReminderSettings,
    private val poster: ReminderPoster,
    private val clock: Clock,
    private val log: (String) -> Unit = {},
) {
    private val mutex = Mutex()

    /** [force] (debug command only) shows it again even when it was shown already. */
    suspend fun deliver(tripUuid: String, uid: String, force: Boolean = false): DeliveryResult = mutex.withLock {
        val trip = trips.trip(tripUuid, uid)
        val now = clock.instant()
        val today = LocalDate.now(clock)
        val key = trip?.let { shownKey(tripUuid, uid, it.startDate) }
        val result = when {
            key != null && !force && key in settings.shown() -> DeliveryResult.ALREADY_SHOWN
            trip != null && !trip.startDate.isAfter(today) -> DeliveryResult.TOO_LATE
            !shouldShowReminder(
                reminderUid = uid,
                signedInUid = authRepository.currentUser.value?.uid,
                tripStart = trip?.startDate,
                today = today,
                enabled = settings.enabled.value,
                notificationsAllowed = poster.canPost(),
            ) -> DeliveryResult.NOT_WANTED
            else -> {
                checkNotNull(trip)
                poster.post(trip, uid, reminderMessage(trips.beforeTripCount(tripUuid, uid)))
                settings.markShown(checkNotNull(key))
                DeliveryResult.POSTED
            }
        }
        val late = trip?.let { Duration.between(reminderTime(it.startDate, clock.zone), now).toMinutes() }
        log("Reminder $tripUuid: $result" + if (result == DeliveryResult.POSTED && late != null && late > 0) " ($late min late)" else "")
        result
    }
}
