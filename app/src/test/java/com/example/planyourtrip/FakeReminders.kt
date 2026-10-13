package com.example.planyourtrip

import com.example.planyourtrip.data.reminder.BeforeTripCount
import com.example.planyourtrip.data.reminder.Reminder
import com.example.planyourtrip.data.reminder.ReminderMessage
import com.example.planyourtrip.data.reminder.ReminderPoster
import com.example.planyourtrip.data.reminder.ReminderPrompt
import com.example.planyourtrip.data.reminder.ReminderSettings
import com.example.planyourtrip.data.reminder.ReminderTrips
import com.example.planyourtrip.data.reminder.ReminderWork
import com.example.planyourtrip.data.reminder.shownKeyStart
import com.example.planyourtrip.data.trip.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

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

    var shownKeys: Set<String> = emptySet()

    override fun shown(): Set<String> = shownKeys

    override fun markShown(key: String) {
        shownKeys = shownKeys + key
    }

    override fun pruneShown(today: LocalDate) {
        shownKeys = shownKeys.filterTo(mutableSetOf()) { shownKeyStart(it)?.isBefore(today) == false }
    }

    override var batteryTipShown: Boolean = false
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

    override fun cancelAll(tripUuids: Collection<String>) {
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

/** [ReminderTrips] over (owner, trip) pairs, e.g. `FakeTripRepository.trips`; "Before the trip" counts by uuid. */
class FakeReminderTrips(private val tripsOf: () -> List<Pair<String, Trip>>) : ReminderTrips {
    val counts = mutableMapOf<String, BeforeTripCount>()

    override suspend fun trip(tripUuid: String, ownerId: String): Trip? =
        tripsOf().firstOrNull { (owner, trip) -> owner == ownerId && trip.uuid == tripUuid }?.second

    override suspend fun beforeTripCount(tripUuid: String, ownerId: String): BeforeTripCount =
        counts[tripUuid] ?: BeforeTripCount(open = 0, total = 0)
}

/** Records every posted reminder ("<uuid>:<message>"); [allowed] = notifications may be shown. */
class FakeReminderPoster(var allowed: Boolean = true) : ReminderPoster {
    val posted = mutableListOf<String>()

    override fun canPost(): Boolean = allowed

    override fun post(trip: Trip, uid: String, message: ReminderMessage) {
        posted += "${trip.uuid}:$message"
    }
}

/** A clock the test moves ([set]) in a fixed zone. */
class MutableClock(var now: Instant, private val zone: ZoneId) : Clock() {
    fun set(time: java.time.LocalDateTime) {
        now = time.atZone(zone).toInstant()
    }

    override fun instant(): Instant = now
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
}
