package com.example.planyourtrip.ui.common

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Pure formatting helpers for trip dates (no Android dependencies, unit-tested). */
object TripFormatting {

    /** Formatters are immutable and thread-safe, so they are built once per locale. */
    private class Formatters(locale: Locale) {
        val day: DateTimeFormatter = DateTimeFormatter.ofPattern("d", locale)
        val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
        val weekdayDayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)
        val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", locale)
        val full: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", locale)
        val field: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }

    private val cache = ConcurrentHashMap<Locale, Formatters>()

    private fun formatters(locale: Locale): Formatters = cache.getOrPut(locale) { Formatters(locale) }

    /** Inclusive length: 12–17 Nov is 6 days. */
    fun lengthInDays(start: LocalDate, end: LocalDate): Int = daysInclusive(start, end)

    /**
     * - same day:   "12 Nov 2026"
     * - same month: "12–17 Nov 2026"
     * - same year:  "28 Nov – 3 Dec 2026"
     * - otherwise:  "28 Dec 2026 – 3 Jan 2027"
     */
    fun dateRange(start: LocalDate, end: LocalDate, locale: Locale = AppLocale.locale): String {
        val f = formatters(locale)
        return when {
            start == end -> start.format(f.full)
            start.year == end.year && start.month == end.month ->
                "${start.format(f.day)}–${end.format(f.full)}"
            start.year == end.year -> "${start.format(f.dayMonth)} – ${end.format(f.full)}"
            else -> "${start.format(f.full)} – ${end.format(f.full)}"
        }
    }

    /** "12 Nov 2026" — e.g. the date of an expense. */
    fun date(date: LocalDate, locale: Locale = AppLocale.locale): String =
        date.format(formatters(locale).full)

    /** "2 Oct" — e.g. the date of an exchange rate. */
    fun dayMonth(date: LocalDate, locale: Locale = AppLocale.locale): String =
        date.format(formatters(locale).dayMonth)

    /** "Thu 12 Nov" — itinerary day chips and the Overview day section. */
    fun weekdayDayMonth(date: LocalDate, locale: Locale = AppLocale.locale): String =
        date.format(formatters(locale).weekdayDayMonth)

    /** "13:00" — activity start times, always 24-hour. */
    fun time(time: LocalTime, locale: Locale = AppLocale.locale): String = time.format(formatters(locale).time)

    /** "Aug 2026" — used for past trips. */
    fun monthYear(date: LocalDate, locale: Locale = AppLocale.locale): String =
        date.format(formatters(locale).monthYear)

    /** Medium date for form fields: "Nov 12, 2026" in the app locale. */
    fun fieldDate(date: LocalDate, locale: Locale = AppLocale.locale): String =
        date.format(formatters(locale).field)
}
