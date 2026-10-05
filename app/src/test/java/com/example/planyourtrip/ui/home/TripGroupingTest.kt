package com.example.planyourtrip.ui.home

import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.TripStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TripGroupingTest {

    private val today = LocalDate.of(2026, 10, 5)

    private fun trip(id: Long, start: LocalDate, end: LocalDate) =
        Trip(id, "Trip $id", start, end, TripColor.SKY)

    @Test
    fun emptyList_isEmpty() {
        val sections = groupTrips(emptyList(), today)
        assertTrue(sections.isEmpty)
        assertNull(sections.next)
    }

    @Test
    fun futureTrips_nearestIsNext_restUpcomingInOrder() {
        val a = trip(1, today.plusDays(120), today.plusDays(124))
        val b = trip(2, today.plusDays(38), today.plusDays(43))
        val c = trip(3, today.plusDays(196), today.plusDays(202))

        val sections = groupTrips(listOf(a, b, c), today)

        assertEquals(ActiveTrip(b, TripStatus.InDays(38)), sections.next)
        assertEquals(listOf(a, c), sections.upcoming.map { it.trip })
        assertEquals(listOf(TripStatus.InDays(120), TripStatus.InDays(196)), sections.upcoming.map { it.status })
        assertTrue(sections.past.isEmpty())
    }

    @Test
    fun tripStartingToday_isNow() {
        val t = trip(1, today, today.plusDays(3))
        assertEquals(TripStatus.Now, groupTrips(listOf(t), today).next?.status)
    }

    @Test
    fun tripEndingToday_isStillCurrent() {
        val t = trip(1, today.minusDays(4), today)
        val sections = groupTrips(listOf(t), today)
        assertEquals(TripStatus.Now, sections.next?.status)
        assertTrue(sections.past.isEmpty())
    }

    @Test
    fun ongoingTrip_beatsFutureTripAsNext() {
        val future = trip(1, today.plusDays(1), today.plusDays(2))
        val ongoing = trip(2, today.minusDays(1), today.plusDays(1))

        val sections = groupTrips(listOf(future, ongoing), today)

        assertEquals(ongoing, sections.next?.trip)
        assertEquals(listOf(ActiveTrip(future, TripStatus.InDays(1))), sections.upcoming)
    }

    @Test
    fun secondOngoingTrip_goesToUpcomingWithNow() {
        val first = trip(1, today.minusDays(3), today.plusDays(3))
        val second = trip(2, today.minusDays(1), today.plusDays(1))

        val sections = groupTrips(listOf(second, first), today)

        assertEquals(first, sections.next?.trip)
        assertEquals(listOf(ActiveTrip(second, TripStatus.Now)), sections.upcoming)
    }

    @Test
    fun pastTrips_mostRecentFirst() {
        val older = trip(1, LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 5))
        val recent = trip(2, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14))
        val yesterday = trip(3, today.minusDays(3), today.minusDays(1))

        val sections = groupTrips(listOf(older, yesterday, recent), today)

        assertNull(sections.next)
        assertEquals(listOf(yesterday, recent, older), sections.past)
    }

    @Test
    fun sameStartDate_orderedById() {
        val b = trip(2, today.plusDays(5), today.plusDays(6))
        val a = trip(1, today.plusDays(5), today.plusDays(9))
        val sections = groupTrips(listOf(b, a), today)
        assertEquals(a, sections.next?.trip)
        assertEquals(listOf(b), sections.upcoming.map { it.trip })
    }
}
