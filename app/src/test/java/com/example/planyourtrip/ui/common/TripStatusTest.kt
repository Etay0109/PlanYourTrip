package com.example.planyourtrip.ui.common

import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TripStatusTest {
    private val trip = Trip(1, "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY)

    @Test
    fun beforeTrip_countsDaysToStart() {
        assertEquals(TripStatus.InDays(38), tripStatus(trip, LocalDate.of(2026, 10, 5)))
        assertEquals(TripStatus.InDays(1), tripStatus(trip, LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun duringTrip_isNow_includingFirstAndLastDay() {
        assertEquals(TripStatus.Now, tripStatus(trip, LocalDate.of(2026, 11, 12)))
        assertEquals(TripStatus.Now, tripStatus(trip, LocalDate.of(2026, 11, 17)))
    }

    @Test
    fun afterTrip_hasNoStatus() {
        assertNull(tripStatus(trip, LocalDate.of(2026, 11, 18)))
    }
}
