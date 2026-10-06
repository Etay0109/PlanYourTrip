package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.ui.common.activitiesOn
import com.example.planyourtrip.ui.common.dayNumber
import com.example.planyourtrip.ui.common.isWithinTrip
import com.example.planyourtrip.ui.common.sortActivities
import com.example.planyourtrip.ui.common.tripDays
import java.time.LocalDate

// Pure itinerary rules (no Android, unit-tested in ItineraryTest).

/** Trips longer than this show one continuous progress bar instead of one segment per day. */
const val MAX_PROGRESS_SEGMENTS = 14

/** The Overview day section shows this many activities, then "+N more". */
const val OVERVIEW_DAY_MAX_ACTIVITIES = 3

/** Activities whose date is no longer inside the trip (after the dates were edited), in timeline order. */
fun outsideTripDates(activities: List<TripActivity>, start: LocalDate, end: LocalDate): List<TripActivity> =
    sortActivities(activities.filterNot { isWithinTrip(it.date, start, end) })

/** Trip days with at least one activity. Activities outside the trip dates never count. */
fun plannedDays(start: LocalDate, end: LocalDate, activities: List<TripActivity>): Set<LocalDate> =
    activities.mapTo(mutableSetOf()) { it.date }.filterTo(mutableSetOf()) { isWithinTrip(it, start, end) }

/** "5 of 6 days planned": one flag per trip day, true when that day is planned. */
data class PlanProgress(val dayPlanned: List<Boolean>) {
    val totalDays: Int get() = dayPlanned.size
    val plannedDays: Int get() = dayPlanned.count { it }
    val openDays: Int get() = totalDays - plannedDays

    /** One segment per day; longer trips get one continuous bar. */
    val segmented: Boolean get() = totalDays <= MAX_PROGRESS_SEGMENTS
    val fraction: Float get() = if (totalDays == 0) 0f else plannedDays.toFloat() / totalDays
}

fun planProgress(start: LocalDate, end: LocalDate, activities: List<TripActivity>): PlanProgress {
    val planned = plannedDays(start, end, activities)
    return PlanProgress(tripDays(start, end).map { it in planned })
}

/** Today while the trip is happening; otherwise (before or after it) the first day. */
fun defaultDay(start: LocalDate, end: LocalDate, today: LocalDate): LocalDate =
    if (isWithinTrip(today, start, end)) today else start

/** Keeps [selected] while it is a trip day; otherwise (none yet, or the dates changed) the [defaultDay]. */
fun validDayOrDefault(selected: LocalDate?, start: LocalDate, end: LocalDate, today: LocalDate): LocalDate =
    selected?.takeIf { isWithinTrip(it, start, end) } ?: defaultDay(start, end, today)

/** Title of the Overview day section, mapped to a string in the UI. */
sealed interface DayTitle {
    /** Day 1, before the trip starts. */
    data object FirstDay : DayTitle

    /** The current day, during the trip. */
    data object Today : DayTitle

    data class DayNumber(val number: Int) : DayTitle
}

fun daySectionTitle(day: LocalDate, start: LocalDate, end: LocalDate, today: LocalDate): DayTitle = when {
    day == start && today.isBefore(start) -> DayTitle.FirstDay
    day == today && isWithinTrip(today, start, end) -> DayTitle.Today
    else -> DayTitle.DayNumber(dayNumber(day, start))
}

/** Sum of the estimated costs, in agorot; null when no activity has a cost (the UI hides it). */
fun dailyCostAgorot(activities: List<TripActivity>): Long? {
    val costs = activities.mapNotNull { it.estimatedCostAgorot }
    return if (costs.isEmpty()) null else costs.sum()
}

/**
 * Builds the Plan tab and Overview itinerary state. [planDay] / [overviewDay] are the user's selections (null =
 * none yet); a selection outside the trip dates falls back to the [defaultDay].
 */
fun itineraryState(
    start: LocalDate,
    end: LocalDate,
    activities: List<TripActivity>,
    planDay: LocalDate?,
    overviewDay: LocalDate?,
    today: LocalDate,
): ItineraryState {
    val plan = validDayOrDefault(planDay, start, end, today)
    val planActivities = activitiesOn(plan, activities)
    val overview = validDayOrDefault(overviewDay, start, end, today)
    val overviewActivities = activitiesOn(overview, activities)
    return ItineraryState(
        days = tripDays(start, end),
        hasActivities = activities.isNotEmpty(),
        progress = planProgress(start, end, activities),
        plan = PlanDayState(
            day = plan,
            dayNumber = dayNumber(plan, start),
            activities = planActivities,
            costAgorot = dailyCostAgorot(planActivities),
        ),
        overviewDay = OverviewDayState(
            day = overview,
            title = daySectionTitle(overview, start, end, today),
            activities = overviewActivities.take(OVERVIEW_DAY_MAX_ACTIVITIES),
            moreCount = (overviewActivities.size - OVERVIEW_DAY_MAX_ACTIVITIES).coerceAtLeast(0),
            hasPrevious = overview.isAfter(start),
            hasNext = overview.isBefore(end),
        ),
        outside = outsideTripDates(activities, start, end),
    )
}
