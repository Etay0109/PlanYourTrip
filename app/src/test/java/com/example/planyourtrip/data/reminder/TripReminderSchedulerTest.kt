package com.example.planyourtrip.data.reminder

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeReminderSettings
import com.example.planyourtrip.FakeReminderWork
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TripReminderSchedulerTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val clock = Clock.fixed(LocalDateTime.of(2026, 10, 12, 10, 0).atZone(zone).toInstant(), zone)
    private val user = AuthUser("u1", "Yael", "yael@example.com")

    private val auth = FakeAuthRepository(user)
    private val trips = FakeTripRepository()
    private val settings = FakeReminderSettings()
    private val work = FakeReminderWork()

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

    private fun TestScope.newScheduler() = TripReminderScheduler(auth, trips, settings, work, clock, backgroundScope)

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
    fun atStart_aReminderTheSavedListHasButWorkManagerLost_isScheduledAgain() = runTest {
        setTrips(trip("a", nov1), trip("b", nov8))
        settings.saved = mapOf("a" to reminderOn(nov1), "b" to reminderOn(nov8))
        work.pending["b"] = reminderOn(nov8) // only "b" is really pending
        start()
        assertEquals(mapOf("a" to reminderOn(nov1), "b" to reminderOn(nov8)), work.pending)
        assertEquals(listOf("schedule:a"), work.calls)
    }

    @Test
    fun atStart_aPendingReminderNoTripWants_isCancelled() = runTest {
        setTrips(trip("a", nov1))
        work.pending["gone"] = reminderOn(nov8) // unknown to the saved list too
        work.pending["a"] = reminderOn(nov1)
        settings.saved = mapOf("a" to reminderOn(nov1))
        start()
        assertEquals(mapOf("a" to reminderOn(nov1)), work.pending)
        assertEquals(listOf("cancel:gone"), work.calls)
    }

    @Test
    fun afterTheStartUpCheck_theSavedListIsUsed() = runTest {
        setTrips(trip("a", nov1))
        start()
        // WorkManager loses it later in the same process: not re-read on every change (only at the next start).
        work.drop("a")
        setTrips(trip("a", nov1), trip("c", nov8))
        assertEquals(listOf("schedule:a", "schedule:c"), work.calls)
        // The next app start finds it missing and schedules it again.
        work.calls.clear()
        start(newScheduler())
        assertEquals(listOf("schedule:a"), work.calls)
    }
}
