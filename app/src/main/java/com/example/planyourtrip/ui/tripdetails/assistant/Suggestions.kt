package com.example.planyourtrip.ui.tripdetails.assistant

import com.example.planyourtrip.data.assistant.SuggestedPlan
import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.ui.common.isWithinTrip
import java.time.LocalDate

// Pure conversion of a suggested plan into itinerary activities (unit-tested in SuggestionsTest).

/**
 * The [checked] suggestions (indexes into [SuggestedPlan.activities]) as normal activities on the plan's day,
 * not booked and without a location. Empty when the day is outside the trip, so nothing is ever added there.
 */
fun suggestionInputs(
    plan: SuggestedPlan,
    checked: Set<Int>,
    tripStart: LocalDate,
    tripEnd: LocalDate,
): List<ActivityInput> {
    if (!isWithinTrip(plan.date, tripStart, tripEnd)) return emptyList()
    return plan.activities.filterIndexed { index, _ -> index in checked }.map { suggestion ->
        ActivityInput(
            date = plan.date,
            startTime = suggestion.startTime,
            title = suggestion.title,
            type = suggestion.type,
            note = suggestion.note,
            location = null,
            estimatedCostAgorot = suggestion.estimatedCostAgorot,
            booked = false,
        )
    }
}
