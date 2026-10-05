package com.example.planyourtrip.ui.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Pure formatting helpers for trip dates (no Android dependencies, unit-tested). */
object TripFormatting {

    /** Formatters are immutable and thread-safe, so they are built once per locale. */
    private class Formatters(locale: Locale) {
        val day: DateTimeFormatter = DateTimeFormatter.ofPattern("d", locale)
        val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
        val full: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy", locale)
        val field: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }

    private val cache = ConcurrentHashMap<Locale, Formatters>()

    private fun formatters(locale: Locale): Formatters = cache.getOrPut(locale) { Formatters(locale) }

    /** Inclusive length: 12–17 Nov is 6 days. */
    fun lengthInDays(start: LocalDate, end: LocalDate): Int =
        ChronoUnit.DAYS.between(start, end).toInt() + 1

    /**
     * - same day:   "12 Nov 2026"
     * - same month: "12–17 Nov 2026"
     * - same year:  "28 Nov – 3 Dec 2026"
     * - otherwise:  "28 Dec 2026 – 3 Jan 2027"
     */
    fun dateRange(start: LocalDate, end: LocalDate, locale: Locale = Locale.getDefault()): String {
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
    fun date(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(formatters(locale).full)

    /** "2 Oct" — e.g. the date of an exchange rate. */
    fun dayMonth(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(formatters(locale).dayMonth)

    /** "Aug 2026" — used for past trips. */
    fun monthYear(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(formatters(locale).monthYear)

    /** Localized medium date for form fields, e.g. "Nov 12, 2026" in US English. */
    fun fieldDate(date: LocalDate, locale: Locale = Locale.getDefault(Locale.Category.FORMAT)): String =
        date.format(formatters(locale).field)
}
