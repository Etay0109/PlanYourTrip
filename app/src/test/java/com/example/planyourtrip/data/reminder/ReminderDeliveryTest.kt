package com.example.planyourtrip.data.reminder

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeReminderPoster
import com.example.planyourtrip.FakeReminderSettings
import com.example.planyourtrip.FakeReminderTrips
import com.example.planyourtrip.MutableClock
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderDeliveryTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val clock = MutableClock(LocalDateTime.of(2026, 10, 13, 18, 0).atZone(zone).toInstant(), zone)
    private val auth = FakeAuthRepository(AuthUser("u1", "Yael", "yael@example.com"))
    private val settings = FakeReminderSettings()
    private val poster = FakeReminderPoster()
    private val oct14 = LocalDate.of(2026, 10, 14)
    private var tripList = listOf("u1" to trip(oct14))
    private val trips = FakeReminderTrips { tripList }
    private val delivery = ReminderDelivery(auth, trips, settings, poster, clock)

    private fun trip(start: LocalDate) =
        Trip(id = 1, destination = "Rome", startDate = start, endDate = start.plusDays(3), color = TripColor.SKY, uuid = "t")

    @Test
    fun shownAtMostOnce_evenWhenTheAlarmAndTheWorkerFireTogether() = runTest {
        val results = List(3) { async { delivery.deliver("t", "u1") } }.awaitAll()
        assertEquals(listOf(DeliveryResult.POSTED, DeliveryResult.ALREADY_SHOWN, DeliveryResult.ALREADY_SHOWN), results)
        clock.set(LocalDateTime.of(2026, 10, 13, 22, 0))
        assertEquals(DeliveryResult.ALREADY_SHOWN, delivery.deliver("t", "u1"))
        assertEquals(1, poster.posted.size)
        assertEquals(setOf(shownKey("t", "u1", oct14)), settings.shownKeys)
    }

    @Test
    fun lateUntil235959_neverAfterMidnight() = runTest {
        clock.set(LocalDateTime.of(2026, 10, 13, 23, 59, 59))
        assertEquals(DeliveryResult.POSTED, delivery.deliver("t", "u1"))

        settings.shownKeys = emptySet()
        clock.set(LocalDateTime.of(2026, 10, 14, 0, 0))
        assertEquals(DeliveryResult.TOO_LATE, delivery.deliver("t", "u1"))
        clock.set(LocalDateTime.of(2026, 10, 14, 9, 0))
        assertEquals(DeliveryResult.TOO_LATE, delivery.deliver("t", "u1"))
        assertEquals(1, poster.posted.size)
    }

    @Test
    fun notWantedNow_isNotRemembered_soItCanStillShowLaterThatEvening() = runTest {
        poster.allowed = false
        assertEquals(DeliveryResult.NOT_WANTED, delivery.deliver("t", "u1"))
        settings.setEnabled(false)
        poster.allowed = true
        assertEquals(DeliveryResult.NOT_WANTED, delivery.deliver("t", "u1"))
        settings.setEnabled(true)
        auth.currentUser.value = AuthUser("u2", "Noa", "noa@example.com")
        assertEquals(DeliveryResult.NOT_WANTED, delivery.deliver("t", "u1")) // another account signed in
        auth.currentUser.value = null
        assertEquals(DeliveryResult.NOT_WANTED, delivery.deliver("t", "u1")) // signed out
        assertTrue(settings.shownKeys.isEmpty())

        auth.currentUser.value = AuthUser("u1", "Yael", "yael@example.com")
        assertEquals(DeliveryResult.POSTED, delivery.deliver("t", "u1"))
    }

    @Test
    fun aDeletedOrMovedTrip_showsNothing() = runTest {
        tripList = listOf("u1" to trip(LocalDate.of(2026, 10, 20)))
        assertEquals(DeliveryResult.NOT_WANTED, delivery.deliver("t", "u1"))
        tripList = emptyList()
        assertEquals(DeliveryResult.NOT_WANTED, delivery.deliver("t", "u1"))
        assertTrue(poster.posted.isEmpty())
    }

    @Test
    fun theMessage_countsTheBeforeTheTripListNow() = runTest {
        trips.counts["t"] = BeforeTripCount(open = 2, total = 5)
        delivery.deliver("t", "u1")
        assertEquals(listOf("t:${ReminderMessage.ItemsLeft(2)}"), poster.posted)
    }

    @Test
    fun force_showsItAgain_debugOnly() = runTest {
        delivery.deliver("t", "u1")
        assertEquals(DeliveryResult.POSTED, delivery.deliver("t", "u1", force = true))
        assertEquals(2, poster.posted.size)
    }
}
