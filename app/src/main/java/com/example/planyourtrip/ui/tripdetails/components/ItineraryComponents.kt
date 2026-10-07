package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroLabelStyle
import com.example.planyourtrip.ui.tripdetails.DayTitle
import com.example.planyourtrip.ui.tripdetails.OverviewDayState
import com.example.planyourtrip.ui.tripdetails.PlanDayState
import com.example.planyourtrip.ui.tripdetails.PlanProgress
import java.time.LocalDate

/** Callbacks of the Plan tab and the Overview itinerary parts, grouped so the screen passes one value. */
class ItineraryActions(
    val onPlanDaySelected: (LocalDate) -> Unit,
    val onOverviewPreviousDay: () -> Unit,
    val onOverviewNextDay: () -> Unit,
    /** Opens the Plan tab on the day shown in the Overview. */
    val onSeeFullPlan: () -> Unit,
    /** Opens the activity form for a new activity on that day. */
    val onAddActivity: (LocalDate) -> Unit,
    val onActivityClick: (Long) -> Unit,
    /** "Open in Maps" on a Plan activity card that has a location. */
    val onOpenInMaps: (TripActivity) -> Unit = {},
)

/** "Itinerary" with the trip dates and destination under it. */
@Composable
fun ItineraryHeader(trip: Trip, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier) {
        Text(
            text = stringResource(R.string.itinerary_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                painter = painterResource(R.drawable.ic_calendar),
                contentDescription = null,
                tint = muted,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(
                    R.string.itinerary_subtitle,
                    TripFormatting.dateRange(trip.startDate, trip.endDate),
                    trip.destination,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = muted,
            )
        }
    }
}

/** Horizontal row of "Day 1 · Thu 12 Nov" chips; the selected one is filled teal with a check icon. */
@Composable
fun DayChipsRow(
    days: List<LocalDate>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opens with the selected day in view (e.g. today in the middle of a long trip).
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = days.indexOf(selected).coerceAtLeast(0))
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(days, key = { _, day -> day.toEpochDay() }) { index, day ->
            DayChip(
                text = stringResource(R.string.itinerary_day_chip, index + 1, TripFormatting.weekdayDayMonth(day)),
                selected = day == selected,
                onClick = { onSelect(day) },
            )
        }
    }
}

@Composable
private fun DayChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
        }
    }
}

/** "DAILY RHYTHM": number of activities and "~₪210 planned" (hidden when no activity has a cost). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DaySummaryCard(plan: PlanDayState, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.itinerary_daily_rhythm),
                style = HeroLabelStyle,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val count = plan.activities.size
                SummaryItem(R.drawable.ic_list, pluralStringResource(R.plurals.itinerary_activity_count, count, count))
                plan.costAgorot?.let { cost ->
                    SummaryItem(R.drawable.ic_payments, stringResource(R.string.itinerary_cost_planned, shekels(cost)))
                }
            }
        }
    }
}

@Composable
private fun SummaryItem(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Full-width light "+ Add activity" button under the timeline. */
@Composable
fun AddActivityButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.itinerary_add_activity),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The day's activities as a timeline (Plan tab). */
@Composable
fun PlanTimeline(
    activities: List<TripActivity>,
    actions: ItineraryActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        activities.forEachIndexed { index, activity ->
            TimelineRow(
                isFirst = index == 0,
                isLast = index == activities.lastIndex,
                dotColor = ExtendedTheme.colors.activityType(activity.type).content,
            ) {
                ActivityCard(
                    activity,
                    onClick = { actions.onActivityClick(activity.id) },
                    onOpenInMaps = { actions.onOpenInMaps(activity) },
                )
            }
        }
    }
}

/**
 * Inside the Daily plan card: one segment per trip day (filled = planned), or one continuous bar for long
 * trips, then "5 days set" and "1 open".
 */
@Composable
fun DailyPlanProgress(progress: PlanProgress, modifier: Modifier = Modifier) {
    val filled = MaterialTheme.colorScheme.primary
    val open = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.plan_days_planned, progress.plannedDays, progress.totalDays)
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large)
            .padding(14.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (progress.segmented) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                progress.dayPlanned.forEach { planned ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .background(if (planned) filled else open, CircleShape)
                    )
                }
            }
        } else {
            LinearProgressIndicator(
                progress = { progress.fraction },
                color = filled,
                trackColor = open,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = pluralStringResource(R.plurals.plan_days_set, progress.plannedDays, progress.plannedDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.plan_days_open, progress.openDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
            )
        }
    }
}

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
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TitleDot()
                Spacer(Modifier.width(10.dp))
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
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(end = 12.dp)) {
                if (state.activities.isEmpty()) {
                    Text(
                        text = stringResource(R.string.itinerary_day_empty_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = muted,
                    )
                    Spacer(Modifier.height(16.dp))
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
                            dotTop = 24.dp,
                            bottomGap = 12.dp,
                        ) {
                            CompactActivityCard(activity, onClick = { actions.onActivityClick(activity.id) })
                        }
                    }
                    if (state.moreCount > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = pluralStringResource(R.plurals.itinerary_more, state.moreCount, state.moreCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = muted,
                            modifier = Modifier.padding(start = 44.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            TextButton(
                onClick = actions.onSeeFullPlan,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp, top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.itinerary_see_full_plan),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun dayTitle(title: DayTitle): String = when (title) {
    DayTitle.FirstDay -> stringResource(R.string.itinerary_first_day)
    DayTitle.Today -> stringResource(R.string.itinerary_today)
    is DayTitle.DayNumber -> stringResource(R.string.itinerary_day_number, title.number)
}
