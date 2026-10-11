package com.example.planyourtrip.ui.tripdetails

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.tripdetails.components.ActivityCard
import com.example.planyourtrip.ui.tripdetails.components.AddActivityButton
import com.example.planyourtrip.ui.tripdetails.components.DayChipsRow
import com.example.planyourtrip.ui.tripdetails.components.DaySummaryCard
import com.example.planyourtrip.ui.tripdetails.components.ItineraryActions
import com.example.planyourtrip.ui.tripdetails.components.PlanTimeline

internal fun LazyListScope.planTab(
    state: TripDetailsUiState.Loaded,
    actions: ItineraryActions,
) {
    val itinerary = state.itinerary
    val plan = itinerary.plan
    // The header above already shows the destination and dates: the day chips come first.
    item(key = "day-chips") {
        DayChipsRow(
            itinerary.days, selected = plan.day, onSelect = actions.onPlanDaySelected,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    item(key = "day-summary") {
        DaySummaryCard(plan, modifier = cardPadding)
    }
    if (plan.activities.isEmpty()) {
        item(key = "day-empty") {
            EmptyMessage(
                icon = R.drawable.ic_event_note,
                title = stringResource(R.string.itinerary_day_empty_title),
                body = stringResource(R.string.itinerary_day_empty_body),
            )
        }
    } else {
        // One item, so the timeline line runs unbroken between the cards.
        item(key = "timeline-${plan.day}") {
            PlanTimeline(plan.activities, actions, modifier = cardPadding)
        }
    }
    item(key = "add-activity") {
        AddActivityButton(onClick = { actions.onAddActivity(plan.day) }, modifier = cardPadding)
    }
    if (itinerary.outside.isNotEmpty()) {
        item(key = "outside-header") {
            Column(cardPadding.padding(top = 16.dp)) {
                Text(
                    text = stringResource(R.string.itinerary_outside_dates),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.itinerary_outside_dates_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(itinerary.outside, key = { "outside-${it.id}" }) { activity ->
            ActivityCard(
                activity,
                onClick = { actions.onActivityClick(activity.id) },
                showDate = true,
                onOpenInMaps = { actions.onOpenInMaps(activity) },
                modifier = cardPadding,
            )
        }
    }
}
