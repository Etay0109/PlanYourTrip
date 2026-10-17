package com.example.planyourtrip.ui.tripdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.tripdetails.components.BudgetCard
import com.example.planyourtrip.ui.tripdetails.components.CurrencyCard
import com.example.planyourtrip.ui.tripdetails.components.CurrencyCardActions
import com.example.planyourtrip.ui.tripdetails.components.DailyPlanProgress
import com.example.planyourtrip.ui.tripdetails.components.DayPreviewCard
import com.example.planyourtrip.ui.tripdetails.components.FeatureCard
import com.example.planyourtrip.ui.tripdetails.components.ItineraryActions
import com.example.planyourtrip.ui.tripdetails.components.OverviewMapIllustration
import com.example.planyourtrip.ui.tripdetails.members.MemberActions
import com.example.planyourtrip.ui.tripdetails.members.MembersCard

internal fun LazyListScope.overviewTab(
    state: TripDetailsUiState.Loaded,
    currencyCard: CurrencyCardState?,
    currencyActions: CurrencyCardActions,
    itineraryActions: ItineraryActions,
    onTabSelected: (TripTab) -> Unit,
    onSetBudget: () -> Unit,
    onOpenMaps: (String) -> Unit,
    memberActions: MemberActions = MemberActions.None,
) {
    val itinerary = state.itinerary
    // "Traveling together" first (design/trip_members.png: only this card is new on the Overview).
    state.members?.let { members ->
        item(key = "members") {
            MembersCard(
                state = members,
                onShowMembers = memberActions.onShowMembers,
                onInvite = memberActions.onInvite,
                modifier = cardPadding.padding(top = 8.dp),
            )
        }
    }
    item(key = "budget") {
        BudgetCard(
            summary = state.summary,
            onSetBudget = onSetBudget,
            onClick = { onTabSelected(TripTab.BUDGET) },
            modifier = if (state.members == null) cardPadding.padding(top = 8.dp) else cardPadding,
        )
    }
    if (currencyCard != null) {
        item(key = "currency") {
            CurrencyCard(currencyCard, currencyActions, modifier = cardPadding)
        }
    }
    item(key = "features") {
        // Equal-height cards side by side, as in the design.
        Row(
            modifier = cardPadding.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The map picture fills whatever height the Daily plan card next to it needs.
            FeatureCard(
                icon = R.drawable.ic_map,
                title = stringResource(R.string.trip_map),
                subtitle = state.trip.destination,
                subtitleMaxLines = 2,
                onClick = { onOpenMaps(state.trip.destination) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                content = {
                    OverviewMapIllustration(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                },
            )
            val progress = itinerary.progress
            val progressContent: (@Composable ColumnScope.() -> Unit)? =
                if (itinerary.hasActivities) ({ DailyPlanProgress(progress) }) else null
            FeatureCard(
                icon = R.drawable.ic_event_note,
                title = stringResource(R.string.trip_daily_plan),
                subtitle = if (itinerary.hasActivities) {
                    pluralStringResource(
                        R.plurals.plan_days_planned, progress.totalDays, progress.plannedDays, progress.totalDays,
                    )
                } else {
                    stringResource(R.string.trip_plan_empty)
                },
                onClick = { onTabSelected(TripTab.PLAN) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                content = progressContent,
            )
        }
    }
    // The day section appears once the trip has at least one activity.
    if (itinerary.hasActivities) {
        item(key = "day-preview") {
            DayPreviewCard(itinerary.overviewDay, itineraryActions, modifier = cardPadding)
        }
    }
}
