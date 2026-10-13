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

/**
 * 00:00 on [start] in [zone]: the reminder's last moment. A reminder is only ever shown the evening before, from 18:00
 * (or late, when it was delayed) until this, never after midnight.
 */
fun reminderDeadline(start: LocalDate, zone: ZoneId): Instant = start.atStartOfDay(zone).toInstant()

/** One wanted (or scheduled) reminder: whose it is and when it fires. */
data class Reminder(val uid: String, val at: Instant)

/** Its time has come (or passed): it should be shown now. */
fun Reminder.isDue(now: Instant): Boolean = !at.isAfter(now)

/**
 * The key a shown reminder is remembered by: trip, account and start date, so a trip whose dates are edited gets a
 * reminder for its new date. "uuid|uid|startEpochDay" (uuids and uids never contain "|").
 */
fun shownKey(tripUuid: String, uid: String, start: LocalDate): String = "$tripUuid|$uid|${start.toEpochDay()}"

/** The start date a [shownKey] was made for, or null when it can't be read. */
fun shownKeyStart(key: String): LocalDate? =
    key.substringAfterLast('|', "").toLongOrNull()?.let(LocalDate::ofEpochDay)

/**
 * The reminders [uid]'s [trips] are still owed now, by trip uuid: only trips that start after today and whose
 * reminder isn't [shown] yet, and whose 18:00 the day before is still ahead, or has passed while the reminder was
 * already [scheduled] for that time (it was delayed: owed until midnight, see [reminderDeadline]). A trip first seen
 * after 18:00 the evening before (created or synced that evening) gets none. Nothing when reminders are off or nobody
 * is signed in.
 */
fun planReminders(
    uid: String?,
    trips: List<Trip>,
    now: Instant,
    zone: ZoneId,
    enabled: Boolean,
    scheduled: Map<String, Reminder> = emptyMap(),
    shown: Set<String> = emptySet(),
): Map<String, Reminder> {
    if (!enabled || uid == null) return emptyMap()
    val today = now.atZone(zone).toLocalDate()
    return trips
        .filter { it.uuid.isNotBlank() && it.startDate.isAfter(today) }
        .filter { shownKey(it.uuid, uid, it.startDate) !in shown }
        .associate { it.uuid to Reminder(uid, reminderTime(it.startDate, zone)) }
        .filter { (uuid, reminder) -> reminder.at.isAfter(now) || scheduled[uuid] == reminder }
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
 * reminder that ran only after midnight, show nothing). Whether it was shown already is [ReminderDelivery]'s check.
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
