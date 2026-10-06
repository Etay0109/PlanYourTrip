package com.example.planyourtrip.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class TripFormattingTest {

    private val en = Locale.ENGLISH
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test
    fun sameMonth() {
        assertEquals("12–17 Nov 2026", TripFormatting.dateRange(d(2026, 11, 12), d(2026, 11, 17), en))
    }

    @Test
    fun sameYearDifferentMonth() {
        assertEquals("28 Nov – 3 Dec 2026", TripFormatting.dateRange(d(2026, 11, 28), d(2026, 12, 3), en))
    }

    @Test
    fun differentYears() {
        assertEquals(
            "28 Dec 2026 – 3 Jan 2027",
            TripFormatting.dateRange(d(2026, 12, 28), d(2027, 1, 3), en),
        )
    }

    @Test
    fun singleDay() {
        assertEquals("12 Nov 2026", TripFormatting.dateRange(d(2026, 11, 12), d(2026, 11, 12), en))
    }

    @Test
    fun date() {
        assertEquals("3 Dec 2026", TripFormatting.date(d(2026, 12, 3), en))
    }

    @Test
    fun monthYear() {
        assertEquals("Aug 2026", TripFormatting.monthYear(d(2026, 8, 10), en))
    }

    @Test
    fun fieldDate_usesLocalizedMediumStyle() {
        assertEquals("Nov 12, 2026", TripFormatting.fieldDate(d(2026, 11, 12), Locale.US))
    }

    @Test
    fun formatters_areCachedPerLocale_withoutMixingLocales() {
        val date = d(2026, 8, 10)
        assertEquals("Aug 2026", TripFormatting.monthYear(date, en))
        assertEquals("août 2026", TripFormatting.monthYear(date, Locale.FRENCH))
        assertEquals("Aug 2026", TripFormatting.monthYear(date, en))
    }

    @Test
    fun weekdayDayMonth() {
        assertEquals("Thu 12 Nov", TripFormatting.weekdayDayMonth(d(2026, 11, 12), en))
    }

    @Test
    fun time_isAlways24Hour() {
        assertEquals("13:00", TripFormatting.time(LocalTime.of(13, 0), Locale.US))
        assertEquals("09:05", TripFormatting.time(LocalTime.of(9, 5), Locale.US))
    }

    @Test
    fun lengthIsInclusive() {
        assertEquals(6, TripFormatting.lengthInDays(d(2026, 11, 12), d(2026, 11, 17)))
        assertEquals(1, TripFormatting.lengthInDays(d(2026, 11, 12), d(2026, 11, 12)))
        assertEquals(7, TripFormatting.lengthInDays(d(2026, 12, 28), d(2027, 1, 3)))
    }

    @Test
    fun pickerMillis_roundTripWithoutShifting() {
        val date = d(2026, 11, 12)
        assertEquals(date, pickerMillisToLocalDate(date.toPickerMillis()))
        // 2026-11-12T00:00Z
        assertEquals(1_794_441_600_000L, date.toPickerMillis())
    }
}
