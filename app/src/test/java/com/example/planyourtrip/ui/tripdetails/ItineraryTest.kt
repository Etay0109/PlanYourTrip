package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.ui.common.activitiesOn
import com.example.planyourtrip.ui.common.dayNumber
import com.example.planyourtrip.ui.common.tripDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ItineraryTest {

    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private val start = nov(12)
    private val end = nov(17)

    private fun activity(
        id: Long,
        date: LocalDate,
        time: LocalTime? = null,
        cost: Long? = null,
    ) = TripActivity(id, 1, date, time, "Activity $id", ActivityType.ACTIVITY, estimatedCostAgorot = cost)

    // ---------- Days ----------

    @Test
    fun tripDays_areInclusive() {
        assertEquals((12..17).map(::nov), tripDays(start, end))
        assertEquals(listOf(nov(12)), tripDays(nov(12), nov(12)))
        assertEquals(LocalDate.of(2027, 1, 2), tripDays(LocalDate.of(2026, 12, 30), LocalDate.of(2027, 1, 2)).last())
    }

    @Test
    fun dayNumber_startsAtOne() {
        assertEquals(1, dayNumber(nov(12), start))
        assertEquals(6, dayNumber(nov(17), start))
    }

    // ---------- Ordering ----------

    @Test
    fun activitiesWithoutTime_comeLast_thenByCreation() {
        val list = listOf(
            activity(1, nov(12)),
            activity(2, nov(12), LocalTime.of(20, 0)),
            activity(3, nov(12), LocalTime.of(9, 30)),
            activity(4, nov(12)),
            activity(5, nov(12), LocalTime.of(9, 30)),
        )
        assertEquals(listOf(3L, 5L, 2L, 1L, 4L), activitiesOn(nov(12), list).map { it.id })
    }

    @Test
    fun activitiesOn_onlyThatDay() {
        val list = listOf(activity(1, nov(12)), activity(2, nov(13)))
        assertEquals(listOf(2L), activitiesOn(nov(13), list).map { it.id })
        assertTrue(activitiesOn(nov(14), list).isEmpty())
    }

    // ---------- Planned days and progress ----------

    @Test
    fun plannedDays_needAtLeastOneActivity() {
        val list = listOf(activity(1, nov(12)), activity(2, nov(12)), activity(3, nov(15)))
        assertEquals(setOf(nov(12), nov(15)), plannedDays(start, end, list))

        val progress = planProgress(start, end, list)
        assertEquals(listOf(true, false, false, true, false, false), progress.dayPlanned)
        assertEquals(2, progress.plannedDays)
        assertEquals(4, progress.openDays)
        assertEquals(6, progress.totalDays)
        assertTrue(progress.segmented)
    }

    @Test
    fun oneDayTrip_progress() {
        val progress = planProgress(nov(12), nov(12), listOf(activity(1, nov(12))))
        assertEquals(1, progress.totalDays)
        assertEquals(1, progress.plannedDays)
        assertEquals(0, progress.openDays)
        assertEquals(1f, progress.fraction)
    }

    @Test
    fun longTrips_useOneContinuousBar() {
        assertTrue(planProgress(nov(1), nov(14), emptyList()).segmented) // 14 days
        val longTrip = planProgress(nov(1), nov(15), listOf(activity(1, nov(1)), activity(2, nov(2)), activity(3, nov(3))))
        assertFalse(longTrip.segmented) // 15 days
        assertEquals(0.2f, longTrip.fraction)
    }

    // ---------- Outside trip dates ----------

    @Test
    fun activitiesOutsideTheDates_areGroupedSeparately_andNeverCountAsPlanned() {
        val list = listOf(
            activity(1, nov(10), LocalTime.of(18, 0)),
            activity(2, nov(13)),
            activity(3, nov(20)),
            activity(4, nov(10), LocalTime.of(8, 0)),
        )
        assertEquals(setOf(nov(13)), plannedDays(start, end, list))
        assertEquals(listOf(4L, 1L, 3L), outsideTripDates(list, start, end).map { it.id })
        assertEquals(1, planProgress(start, end, list).plannedDays)
    }

    @Test
    fun outside_isEmptyWhenEverythingFits() {
        assertTrue(outsideTripDates(listOf(activity(1, nov(12)), activity(2, nov(17))), start, end).isEmpty())
    }

    // ---------- Default day and titles ----------

    @Test
    fun defaultDay_beforeTheTrip_isTheFirstDay() {
        assertEquals(nov(12), defaultDay(start, end, today = LocalDate.of(2026, 10, 5)))
        assertEquals(DayTitle.FirstDay, daySectionTitle(nov(12), start, end, LocalDate.of(2026, 10, 5)))
        assertEquals(DayTitle.DayNumber(2), daySectionTitle(nov(13), start, end, LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun defaultDay_duringTheTrip_isToday() {
        val today = nov(14)
        assertEquals(nov(14), defaultDay(start, end, today))
        assertEquals(DayTitle.Today, daySectionTitle(nov(14), start, end, today))
        // Day 1 is no longer the "First day" once the trip has started.
        assertEquals(DayTitle.DayNumber(1), daySectionTitle(nov(12), start, end, today))
        assertEquals(DayTitle.DayNumber(4), daySectionTitle(nov(15), start, end, today))
    }

    @Test
    fun tripStartingToday_showsToday() {
        assertEquals(nov(12), defaultDay(start, end, nov(12)))
        assertEquals(DayTitle.Today, daySectionTitle(nov(12), start, end, nov(12)))
    }

    @Test
    fun pastTrip_defaultsToTheFirstDay_titledDayOne() {
        val today = nov(30)
        assertEquals(nov(12), defaultDay(start, end, today))
        assertEquals(DayTitle.DayNumber(1), daySectionTitle(nov(12), start, end, today))
        assertEquals(DayTitle.DayNumber(6), daySectionTitle(nov(17), start, end, today))
    }

    @Test
    fun oneDayTrip_titles() {
        assertEquals(DayTitle.FirstDay, daySectionTitle(nov(12), nov(12), nov(12), nov(1)))
        assertEquals(DayTitle.Today, daySectionTitle(nov(12), nov(12), nov(12), nov(12)))
        assertEquals(DayTitle.DayNumber(1), daySectionTitle(nov(12), nov(12), nov(12), nov(13)))
    }

    @Test
    fun selection_isKeptInsideTheDates_otherwiseDefault() {
        val today = LocalDate.of(2026, 10, 5)
        assertEquals(nov(15), validDayOrDefault(nov(15), start, end, today))
        assertEquals(nov(12), validDayOrDefault(nov(20), start, end, today))
        assertEquals(nov(12), validDayOrDefault(null, start, end, today))
    }

    // ---------- Cost ----------

    @Test
    fun dailyCost_sumsOnlyActivitiesWithACost() {
        assertEquals(21_000L, dailyCostAgorot(listOf(activity(1, nov(12), cost = 9_000), activity(2, nov(12)), activity(3, nov(12), cost = 12_000))))
        assertNull(dailyCostAgorot(listOf(activity(1, nov(12)), activity(2, nov(12)))))
        assertNull(dailyCostAgorot(emptyList()))
    }

    // ---------- Whole state ----------

    @Test
    fun itineraryState_overviewShowsThreeAndCountsTheRest() {
        val list = (1L..5L).map { activity(it, nov(12), LocalTime.of(8 + it.toInt(), 0), cost = 1_000) } +
            activity(6, nov(13))
        val state = itineraryState(start, end, list, planDay = null, overviewDay = null, today = nov(1))

        assertEquals(nov(12), state.plan.day)
        assertEquals(1, state.plan.dayNumber)
        assertEquals(5, state.plan.activities.size)
        assertEquals(5_000L, state.plan.costAgorot)

        val overview = state.overviewDay
        assertEquals(DayTitle.FirstDay, overview.title)
        assertEquals(listOf(1L, 2L, 3L), overview.activities.map { it.id })
        assertEquals(2, overview.moreCount)
        assertFalse(overview.hasPrevious)
        assertTrue(overview.hasNext)
        assertTrue(state.hasActivities)
    }

    @Test
    fun itineraryState_lastDayHasNoNext_andEmptyDayHasNoCost() {
        val state = itineraryState(start, end, listOf(activity(1, nov(12))), planDay = nov(17), overviewDay = nov(17), today = nov(1))
        assertTrue(state.plan.activities.isEmpty())
        assertNull(state.plan.costAgorot)
        assertTrue(state.overviewDay.hasPrevious)
        assertFalse(state.overviewDay.hasNext)
        assertEquals(0, state.overviewDay.moreCount)
    }

    @Test
    fun itineraryState_withoutActivities() {
        val state = itineraryState(nov(12), nov(12), emptyList(), null, null, nov(1))
        assertFalse(state.hasActivities)
        assertEquals(listOf(nov(12)), state.days)
        assertFalse(state.overviewDay.hasPrevious)
        assertFalse(state.overviewDay.hasNext)
    }
}
