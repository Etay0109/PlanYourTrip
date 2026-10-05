package com.example.planyourtrip.data

import com.example.planyourtrip.data.trip.Converters
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.user.firstName
import com.example.planyourtrip.data.user.greetingName
import com.example.planyourtrip.data.user.initials
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UserNamesAndColorTest {

    @Test
    fun firstName_andInitials() {
        assertEquals("Etay", firstName("Etay Zahavi"))
        assertEquals("EZ", initials("Etay Zahavi"))
    }

    @Test
    fun names_handleEdgeCases() {
        assertEquals("Etay", firstName("  Etay   Zahavi "))
        assertEquals("E", initials("etay"))
        assertEquals("AC", initials("Anna Maria Cohen"))
        assertEquals("", firstName("   "))
        assertEquals("?", initials(""))
    }

    @Test
    fun tripColor_roundRobin() {
        val size = TripColor.entries.size
        assertEquals(TripColor.entries[0], TripColor.forIndex(0))
        assertEquals(TripColor.entries[1], TripColor.forIndex(1))
        assertEquals(TripColor.entries[0], TripColor.forIndex(size))
        assertEquals(TripColor.entries[size - 1], TripColor.forIndex(-1))
    }

    @Test
    fun converters_roundTrip() {
        val c = Converters()
        val date = LocalDate.of(2026, 11, 12)
        assertEquals(date, c.epochDayToLocalDate(c.localDateToEpochDay(date)))
        TripColor.entries.forEach { assertEquals(it, c.nameToTripColor(c.tripColorToName(it))) }
        assertEquals(TripColor.entries.first(), c.nameToTripColor("REMOVED_COLOR"))
    }

    @Test
    fun greetingName_prefersDisplayName_thenEmailPrefix() {
        assertEquals("Etay Zahavi", AuthUser("u", "  Etay Zahavi ", "e@x.com").greetingName())
        assertEquals("yael.s", AuthUser("u", null, "yael.s@example.com").greetingName())
        assertEquals("yael.s", AuthUser("u", "   ", "yael.s@example.com").greetingName())
        assertEquals("", AuthUser("u", null, null).greetingName())
    }
}
