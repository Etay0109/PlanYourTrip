package com.example.planyourtrip.ui.common

import com.example.planyourtrip.data.trip.TripActivity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Pure trip-day rules shared by the trip screen, the assistant and the shared PDF (no Android, unit-tested).

/** Every day of the trip, first to last (inclusive: a one-day trip has one day). */
fun tripDays(start: LocalDate, end: LocalDate): List<LocalDate> =
    generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()

/** 1-based number of [day] in a trip starting on [start] ("Day 1" is the start date). */
fun dayNumber(day: LocalDate, start: LocalDate): Int = ChronoUnit.DAYS.between(start, day).toInt() + 1

fun isWithinTrip(date: LocalDate, start: LocalDate, end: LocalDate): Boolean =
    !date.isBefore(start) && !date.isAfter(end)

/** By date, then start time with activities without a time last, then by id (creation order). */
fun sortActivities(activities: List<TripActivity>): List<TripActivity> =
    activities.sortedWith(
        compareBy<TripActivity> { it.date }
            .thenBy(nullsLast()) { it.startTime }
            .thenBy { it.id }
    )

/** The activities of [day], in timeline order. */
fun activitiesOn(day: LocalDate, activities: List<TripActivity>): List<TripActivity> =
    sortActivities(activities.filter { it.date == day })
