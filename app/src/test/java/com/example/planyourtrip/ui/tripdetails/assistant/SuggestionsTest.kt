package com.example.planyourtrip.ui.tripdetails.assistant

import com.example.planyourtrip.data.assistant.SuggestedActivity
import com.example.planyourtrip.data.assistant.SuggestedPlan
import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class SuggestionsTest {

    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private val start = nov(12)
    private val end = nov(17)

    private val plan = SuggestedPlan(
        date = nov(13),
        title = "Vatican day",
        activities = listOf(
            SuggestedActivity(LocalTime.of(9, 30), "Vatican Museums", ActivityType.ACTIVITY, "Book ahead", 9_000),
            SuggestedActivity(LocalTime.of(13, 30), "Lunch at Borgo Pio", ActivityType.DINING, null, null),
            SuggestedActivity(null, "Castel Sant'Angelo", ActivityType.OTHER, "Sunset", 2_000),
        ),
    )

    @Test
    fun onlyCheckedSuggestions_becomeActivitiesOnThePlanDay() {
        assertEquals(
            listOf(
                ActivityInput(nov(13), LocalTime.of(9, 30), "Vatican Museums", ActivityType.ACTIVITY, "Book ahead",
                    location = null, estimatedCostAgorot = 9_000, booked = false),
                ActivityInput(nov(13), null, "Castel Sant'Angelo", ActivityType.OTHER, "Sunset",
                    location = null, estimatedCostAgorot = 2_000, booked = false),
            ),
            suggestionInputs(plan, checked = setOf(0, 2), start, end),
        )
    }

    @Test
    fun allChecked_addsEverything_inOrder() {
        val inputs = suggestionInputs(plan, setOf(0, 1, 2), start, end)
        assertEquals(listOf("Vatican Museums", "Lunch at Borgo Pio", "Castel Sant'Angelo"), inputs.map { it.title })
    }

    @Test
    fun noneChecked_addsNothing() {
        assertTrue(suggestionInputs(plan, emptySet(), start, end).isEmpty())
    }

    @Test
    fun dayOutsideTheTrip_addsNothing() {
        assertTrue(suggestionInputs(plan.copy(date = nov(18)), setOf(0, 1, 2), start, end).isEmpty())
        assertTrue(suggestionInputs(plan.copy(date = nov(11)), setOf(0), start, end).isEmpty())
        assertFalse(suggestionInputs(plan.copy(date = nov(17)), setOf(0), start, end).isEmpty())
    }

    @Test
    fun planCard_followsTheCurrentTripDates() {
        val card = PlanCardState(plan)
        assertEquals(setOf(0, 1, 2), card.checked)
        val inside = planCard(card, start, end)
        assertEquals(2, inside.dayNumber)
        assertTrue(inside.withinTrip && inside.canAdd)
        // The trip was moved: day 13 is no longer part of it.
        val outside = planCard(card, nov(14), nov(20))
        assertFalse(outside.withinTrip || outside.canAdd)
        assertFalse(planCard(card.copy(checked = emptySet()), start, end).canAdd)
        assertFalse(planCard(card.copy(status = AddStatus.ADDED), start, end).canAdd)
    }
}
