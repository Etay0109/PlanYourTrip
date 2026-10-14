package com.example.planyourtrip.data.reminder

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeReminderPoster
import com.example.planyourtrip.FakeReminderSettings
import com.example.planyourtrip.FakeReminderTrips
import com.example.planyourtrip.FakeReminderWork
import com.example.planyourtrip.MutableClock
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TripReminderSchedulerTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val clock = MutableClock(LocalDateTime.of(2026, 10, 12, 10, 0).atZone(zone).toInstant(), zone)
    private val user = AuthUser("u1", "Yael", "yael@example.com")

    private val auth = FakeAuthRepository(user)
    private val trips = FakeTripRepository()
    private val settings = FakeReminderSettings()
    private val work = FakeReminderWork()
    private val poster = FakeReminderPoster()
    private val delivery = ReminderDelivery(auth, FakeReminderTrips { trips.trips.value }, settings, poster, clock)

    private fun trip(uuid: String, start: LocalDate) =
        Trip(id = uuid.hashCode().toLong(), destination = "Rome", startDate = start, endDate = start.plusDays(3),
            color = TripColor.SKY, uuid = uuid)

    private fun reminderOn(day: LocalDate) = Reminder("u1", reminderTime(day, zone))

    private val nov1 = LocalDate.of(2026, 11, 1)
    private val nov8 = LocalDate.of(2026, 11, 8)

    private fun TestScope.start(scheduler: TripReminderScheduler = newScheduler()) {
        scheduler.start()
        runCurrent()
    }

    private fun TestScope.newScheduler() = TripReminderScheduler(auth, trips, settings, work, delivery, clock, backgroundScope)

    private fun TestScope.setTrips(vararg list: Trip, owner: String = "u1") {
        trips.trips.value = list.map { owner to it }
        runCurrent()
    }

    @Test
    fun aNewTrip_isScheduledOnce_atSixTheEveningBefore() = runTest {
        start()
        setTrips(trip("a", nov1))
        assertEquals(mapOf("a" to reminderOn(nov1)), work.pending)
        assertEquals(mapOf("a" to reminderOn(nov1)), settings.saved)

        // Room emits again (another trip's change, a sync): nothing is enqueued again.
        setTrips(trip("a", nov1), trip("past", LocalDate.of(2026, 1, 1)))
        assertEquals(listOf("schedule:a"), work.calls)
    }

    @Test
    fun editedDates_reschedule_deletedTrip_isCancelled() = runTest {
        start()
        setTrips(trip("a", nov1), trip("b", nov1))
        setTrips(trip("a", nov8))
        assertEquals(mapOf("a" to reminderOn(nov8)), work.pending)
        assertEquals(listOf("schedule:a", "schedule:b", "cancel:b", "schedule:a"), work.calls)
    }

    @Test
    fun aSharedTripSyncedDown_isScheduled_andLeavingIt_cancels() = runTest {
        start()
        setTrips(trip("shared", nov1))
        assertEquals(setOf("shared"), work.pending.keys)
        setTrips() // left or removed: the trip is gone from Room
        assertTrue(work.pending.isEmpty())
    }

    @Test
    fun signOut_cancelsEverything() = runTest {
        start()
        setTrips(trip("a", nov1), trip("b", nov8))
        auth.signOut()
        runCurrent()
        assertTrue(work.pending.isEmpty())
        assertTrue("cancelAll" in work.calls)
        assertTrue(settings.saved.isEmpty())
    }

    @Test
    fun anotherAccountSignsIn_onlyItsTripsAreScheduled() = runTest {
        start()
        trips.trips.value = listOf("u1" to trip("a", nov1), "u2" to trip("b", nov8))
        runCurrent()
        auth.currentUser.value = AuthUser("u2", "Noa", "noa@example.com")
        runCurrent()
        assertEquals(mapOf("b" to Reminder("u2", reminderTime(nov8, zone))), work.pending)
    }

    @Test
    fun switchOff_cancelsAll_switchOn_schedulesAgain() = runTest {
        start()
        setTrips(trip("a", nov1), trip("b", nov8))
        settings.setEnabled(false)
        runCurrent()
        assertTrue(work.pending.isEmpty())
        settings.setEnabled(true)
        runCurrent()
        assertEquals(mapOf("a" to reminderOn(nov1), "b" to reminderOn(nov8)), work.pending)
    }

    @Test
    fun tripsStartingTodayOrTomorrowAfterSix_areNotScheduled() = runTest {
        start()
        setTrips(trip("today", LocalDate.of(2026, 10, 12)), trip("started", LocalDate.of(2026, 10, 1)))
        assertTrue(work.pending.isEmpty())
    }

    // The start-up check against the real WorkManager state.

    @Test
    fun atStart_everyFutureReminderIsArmedAgain_alarmsDontSurviveARestartOrAForceStop() = runTest {
        setTrips(trip("a", nov1), trip("b", nov8))
        settings.saved = mapOf("a" to reminderOn(nov1), "b" to reminderOn(nov8))
        work.pending["b"] = reminderOn(nov8) // WorkManager kept "b" only; neither alarm survived
        start()
        assertEquals(mapOf("a" to reminderOn(nov1), "b" to reminderOn(nov8)), work.pending)
        assertEquals(listOf("schedule:a", "schedule:b"), work.calls)
    }

    @Test
    fun atStart_aPendingReminderNoTripWants_isCancelled() = runTest {
        setTrips(trip("a", nov1))
        work.pending["gone"] = reminderOn(nov8) // unknown to the saved list too
        work.pending["a"] = reminderOn(nov1)
        settings.saved = mapOf("a" to reminderOn(nov1))
        start()
        assertEquals(mapOf("a" to reminderOn(nov1)), work.pending)
        assertEquals(listOf("cancel:gone", "schedule:a"), work.calls)
    }

    @Test
    fun afterTheStartUpPass_onlyChangesAreScheduled_untilTheNextStart() = runTest {
        setTrips(trip("a", nov1))
        start()
        // WorkManager loses it later in the same process: not re-checked on every change (only at the next start).
        work.drop("a")
        setTrips(trip("a", nov1), trip("c", nov8))
        assertEquals(listOf("schedule:a", "schedule:c"), work.calls)
        // The next app start arms everything again.
        work.calls.clear()
        start(newScheduler())
        assertEquals(listOf("schedule:a", "schedule:c"), work.calls)
    }

    @Test
    fun armAgain_exactAlarmsJustAllowed_schedulesEveryFutureReminderAgain_inTheRunningProcess() = runTest {
        val scheduler = newScheduler()
        start(scheduler)
        setTrips(trip("a", nov1), trip("c", nov8))
        work.calls.clear()
        scheduler.armAgain()
        assertEquals(listOf("schedule:a", "schedule:c"), work.calls)
        // Afterwards only changes again.
        work.calls.clear()
        setTrips(trip("a", nov1), trip("c", nov8), trip("d", nov8))
        assertEquals(listOf("schedule:d"), work.calls)
    }

    @Test
    fun armAgain_afterTheReminderWasShown_neverShowsItTwice() = runTest {
        scheduledDuringTheDay()
        clock.set(LocalDateTime.of(2026, 10, 13, 18, 0, 1))
        val scheduler = newScheduler()
        start(scheduler)
        assertEquals(1, poster.posted.size)
        clock.set(LocalDateTime.of(2026, 10, 13, 18, 30))
        scheduler.armAgain()
        assertEquals(1, poster.posted.size)
        assertTrue(work.calls.none { it == "schedule:t" })
    }

    // A late reminder: shown the evening before only, at most once (the 2026-10-13 bug: an app start after 18:00
    // cancelled the reminder WorkManager was just running).

    private val oct14 = LocalDate.of(2026, 10, 14)
    private val tomorrowTrip = trip("t", oct14)

    /** The reminder of [tomorrowTrip] was scheduled during the day, then the app process ended. */
    private fun TestScope.scheduledDuringTheDay(pendingInWorkManager: Boolean = true) {
        clock.set(LocalDateTime.of(2026, 10, 13, 10, 0))
        setTrips(tomorrowTrip)
        settings.saved = mapOf("t" to reminderOn(oct14))
        if (pendingInWorkManager) work.pending["t"] = reminderOn(oct14)
    }

    @Test
    fun coldStartAt1800_causedByTheReminder_showsItOnce_andNeverCancelsIt() = runTest {
        scheduledDuringTheDay()
        // The alarm or the job starts the app at 18:00:00.2; the scheduler's first pass runs before the worker.
        clock.now = reminderOn(oct14).at.plusMillis(200)
        start()
        assertEquals(listOf("t:${ReminderMessage.NoList}"), poster.posted)
        assertTrue("cancel:t" !in work.calls)
        // Then the WorkManager backup and the alarm run too: still one notification.
        assertEquals(DeliveryResult.ALREADY_SHOWN, delivery.deliver("t", "u1"))
        assertEquals(DeliveryResult.ALREADY_SHOWN, delivery.deliver("t", "u1"))
        assertEquals(1, poster.posted.size)
    }

    @Test
    fun startAt1801_afterAForceStopLostEverything_showsTheReminderLate_once() = runTest {
        scheduledDuringTheDay(pendingInWorkManager = false) // a force-stop removed the job and the alarm
        clock.set(LocalDateTime.of(2026, 10, 13, 18, 1))
        start()
        assertEquals(listOf("t:${ReminderMessage.NoList}"), poster.posted)
        assertTrue("cancel:t" !in work.calls)
        // Room emits again (a sync): not shown again; the shown reminder is forgotten from the schedule.
        setTrips(tomorrowTrip, trip("other", nov8))
        assertEquals(1, poster.posted.size)
        assertEquals(setOf("other"), settings.saved.keys)
    }

    @Test
    fun lateReminderAt2330_isShown() = runTest {
        scheduledDuringTheDay()
        clock.set(LocalDateTime.of(2026, 10, 13, 23, 30))
        start()
        assertEquals(1, poster.posted.size)
    }

    @Test
    fun afterMidnight_nothingIsShown_andTheReminderIsDropped() = runTest {
        scheduledDuringTheDay()
        clock.set(LocalDateTime.of(2026, 10, 14, 0, 0, 5))
        start()
        assertTrue(poster.posted.isEmpty())
        assertTrue(work.pending.isEmpty())
        assertTrue(settings.saved.isEmpty())
    }

    @Test
    fun aTripFirstSeenAfterSixTheEveningBefore_getsNoReminder() = runTest {
        clock.set(LocalDateTime.of(2026, 10, 13, 20, 0))
        start()
        setTrips(tomorrowTrip) // created or synced to this phone at 20:00
        assertTrue(poster.posted.isEmpty())
        assertTrue(work.pending.isEmpty())
    }

    @Test
    fun notificationsRefusedAt1800_theReminderStaysOwed_andShowsWhenAllowedLaterThatEvening() = runTest {
        scheduledDuringTheDay()
        poster.allowed = false
        clock.set(LocalDateTime.of(2026, 10, 13, 18, 0))
        start()
        assertTrue(poster.posted.isEmpty())
        assertEquals(setOf("t"), settings.saved.keys)
        poster.allowed = true
        clock.set(LocalDateTime.of(2026, 10, 13, 21, 0))
        start(newScheduler()) // the next app start
        assertEquals(1, poster.posted.size)
    }

    @Test
    fun shownRemindersOfPastTrips_areForgotten() = runTest {
        settings.shownKeys = setOf(shownKey("old", "u1", LocalDate.of(2026, 10, 1)), shownKey("t", "u1", oct14))
        start()
        assertEquals(setOf(shownKey("t", "u1", oct14)), settings.shownKeys)
    }
}
