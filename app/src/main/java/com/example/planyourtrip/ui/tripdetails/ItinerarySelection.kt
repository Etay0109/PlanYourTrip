package com.example.planyourtrip.ui.tripdetails

import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The days chosen on the Plan tab and in the Overview day section; [itineraryState] turns them into what is shown. */
class ItinerarySelection(
    /** The loaded trip's itinerary, or null while it isn't loaded. */
    private val current: () -> ItineraryState?,
) {
    private val _planDay = MutableStateFlow<LocalDate?>(null)
    private val _overviewDay = MutableStateFlow<LocalDate?>(null)

    /** Day chosen on the Plan tab; null = the default day (first day, or today during the trip). */
    val planDay: StateFlow<LocalDate?> = _planDay.asStateFlow()

    /** Day shown in the Overview day section; null = the default day. */
    val overviewDay: StateFlow<LocalDate?> = _overviewDay.asStateFlow()

    /** A day chip on the Plan tab. */
    fun onPlanDaySelected(day: LocalDate) {
        _planDay.value = day
    }

    /** "<" in the Overview day section; does nothing on the first day. */
    fun onOverviewPreviousDay() {
        val shown = current()?.overviewDay ?: return
        if (shown.hasPrevious) _overviewDay.value = shown.day.minusDays(1)
    }

    /** ">" in the Overview day section; does nothing on the last day. */
    fun onOverviewNextDay() {
        val shown = current()?.overviewDay ?: return
        if (shown.hasNext) _overviewDay.value = shown.day.plusDays(1)
    }

    /** "See full plan": the Plan tab opens on the day shown in the Overview (the screen switches the tab). */
    fun onSeeFullPlan() {
        current()?.let { _planDay.value = it.overviewDay.day }
    }
}
