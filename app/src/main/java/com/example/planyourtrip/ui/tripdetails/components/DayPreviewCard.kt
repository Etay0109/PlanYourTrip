package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.tripdetails.DayTitle
import com.example.planyourtrip.ui.tripdetails.OverviewDayState

/**
 * Overview day section: "First day" / "Today" / "Day N", the date between previous/next arrows, up to three
 * activities and "+N more", or an empty message with "Add activity"; "See full plan" at the bottom.
 */
@Composable
fun DayPreviewCard(
    state: OverviewDayState,
    actions: ItineraryActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = Dimens.CardElevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = Spacing.xxl, end = Spacing.m, top = Spacing.l, bottom = Spacing.m)) {
            DayPreviewHeader(state, actions)
            Spacer(Modifier.height(Spacing.m))
            DayPreviewActivities(state, actions)
            SeeFullPlanButton(actions.onSeeFullPlan)
        }
    }
}

/** "First day" / "Today" / "Day N" with the date between the previous and next arrows. */
@Composable
private fun DayPreviewHeader(state: OverviewDayState, actions: ItineraryActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TitleDot()
        Spacer(Modifier.width(Spacing.sPlus))
        Text(
            text = dayTitle(state.title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = actions.onOverviewPreviousDay, enabled = state.hasPrevious) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_left),
                contentDescription = stringResource(R.string.itinerary_previous_day),
                tint = if (state.hasPrevious) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            text = TripFormatting.weekdayDayMonth(state.day),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        IconButton(onClick = actions.onOverviewNextDay, enabled = state.hasNext) {
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = stringResource(R.string.itinerary_next_day),
                tint = if (state.hasNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Up to three activities on the rail and "+N more", or the empty message with "Add activity"; then a divider. */
@Composable
private fun DayPreviewActivities(state: OverviewDayState, actions: ItineraryActions) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(end = Spacing.m)) {
        if (state.activities.isEmpty()) {
            Text(
                text = stringResource(R.string.itinerary_day_empty_title),
                style = MaterialTheme.typography.bodyLarge,
                color = muted,
            )
            Spacer(Modifier.height(Spacing.l))
            PrimaryButton(
                text = stringResource(R.string.itinerary_add_activity),
                onClick = { actions.onAddActivity(state.day) },
                leadingIcon = R.drawable.ic_add,
            )
        } else {
            state.activities.forEachIndexed { index, activity ->
                TimelineRow(
                    isFirst = index == 0,
                    isLast = index == state.activities.lastIndex,
                    dotColor = ExtendedTheme.colors.activityType(activity.type).content,
                    dotTop = Spacing.xxl,
                    bottomGap = Spacing.m,
                ) {
                    CompactActivityCard(activity, onClick = { actions.onActivityClick(activity.id) })
                }
            }
            if (state.moreCount > 0) {
                Spacer(Modifier.height(Spacing.sPlus))
                Text(
                    text = pluralStringResource(R.plurals.itinerary_more, state.moreCount, state.moreCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = muted,
                    modifier = Modifier.padding(start = MoreTextIndent),
                )
            }
        }
        Spacer(Modifier.height(Spacing.l))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** "See full plan →" at the bottom of the card. */
@Composable
private fun SeeFullPlanButton(onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = Spacing.m, top = Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.itinerary_see_full_plan),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(Spacing.xsPlus))
        Icon(
            painter = painterResource(R.drawable.ic_arrow_forward),
            contentDescription = null,
            modifier = Modifier.size(IconSize.m),
        )
    }
}

@Composable
private fun dayTitle(title: DayTitle): String = when (title) {
    DayTitle.FirstDay -> stringResource(R.string.itinerary_first_day)
    DayTitle.Today -> stringResource(R.string.itinerary_today)
    is DayTitle.DayNumber -> stringResource(R.string.itinerary_day_number, title.number)
}

/** "+2 more" lines up with the activity titles (after the rail). */
private val MoreTextIndent = 44.dp
