package com.example.planyourtrip.data.reminder

import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.ui.common.countryFlag
import com.example.planyourtrip.ui.common.shortDestination
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Pure (unit-tested in TripRemindersTest): which trips get a "trip starts tomorrow" reminder, when, and what it says.

/** The reminder's time of day, on the day before the trip starts (device time). */
val REMINDER_TIME: LocalTime = LocalTime.of(18, 0)

/**
 * 18:00 on the day before [start] in [zone]. `atZone` uses the offset valid on that day, so a daylight-saving change
 * between now and then never moves the reminder away from 18:00.
 */
fun reminderTime(start: LocalDate, zone: ZoneId): Instant =
    start.minusDays(1).atTime(REMINDER_TIME).atZone(zone).toInstant()

/** One wanted (or scheduled) reminder: whose it is and when it fires. */
data class Reminder(val uid: String, val at: Instant)

/**
 * The reminders [uid]'s [trips] should have now, by trip uuid: only trips that start after today and whose 18:00 the
 * day before is still ahead (a trip created that evening gets none). Nothing when reminders are off or nobody is
 * signed in.
 */
fun planReminders(
    uid: String?,
    trips: List<Trip>,
    now: Instant,
    zone: ZoneId,
    enabled: Boolean,
): Map<String, Reminder> {
    if (!enabled || uid == null) return emptyMap()
    val today = now.atZone(zone).toLocalDate()
    return trips
        .filter { it.uuid.isNotBlank() && it.startDate.isAfter(today) }
        .associate { it.uuid to Reminder(uid, reminderTime(it.startDate, zone)) }
        .filterValues { it.at.isAfter(now) }
}

/** What to do to go from the [scheduled] reminders to the [desired] ones. */
data class ReminderChanges(
    /** New reminders and ones whose time or account changed (scheduled again, replacing the old one). */
    val toSchedule: Map<String, Reminder>,
    /** Trips that no longer want a reminder (deleted, left, removed, started, or reminders off). */
    val toCancel: Set<String>,
)

fun reminderChanges(desired: Map<String, Reminder>, scheduled: Map<String, Reminder>): ReminderChanges =
    ReminderChanges(
        toSchedule = desired.filter { (uuid, reminder) -> scheduled[uuid] != reminder },
        toCancel = scheduled.keys - desired.keys,
    )

/** The trip's "Before the trip" list when the reminder is shown. */
data class BeforeTripCount(val open: Int, val total: Int)

/** The reminder's text. */
sealed interface ReminderMessage {
    /** The trip has no "Before the trip" items: "Your trip starts tomorrow." */
    data object NoList : ReminderMessage

    /** Everything is checked: "Everything is ready. Have a great trip!" */
    data object AllDone : ReminderMessage

    /** "Your trip starts tomorrow. N things left on your Before the trip list." */
    data class ItemsLeft(val count: Int) : ReminderMessage
}

fun reminderMessage(count: BeforeTripCount): ReminderMessage = when {
    count.total <= 0 -> ReminderMessage.NoList
    count.open <= 0 -> ReminderMessage.AllDone
    else -> ReminderMessage.ItemsLeft(count.open)
}

/**
 * Whether a reminder that fires now is still wanted: reminders are on and notifications allowed, the account it was
 * scheduled for is the one signed in, and its trip still exists for it and starts tomorrow (dates edited since, or a
 * reminder that ran only after midnight, show nothing).
 */
fun shouldShowReminder(
    reminderUid: String,
    signedInUid: String?,
    tripStart: LocalDate?,
    today: LocalDate,
    enabled: Boolean,
    notificationsAllowed: Boolean,
): Boolean =
    enabled && notificationsAllowed && signedInUid != null && signedInUid == reminderUid &&
        tripStart == today.plusDays(1)

/** The title's destination: "🇬🇧 London" for a picked place, the full text otherwise. */
fun reminderDestination(destination: String, countryCode: String?): String =
    listOfNotNull(countryFlag(countryCode), shortDestination(destination, countryCode)).joinToString(" ")
