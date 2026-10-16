package com.example.planyourtrip.ui.tripdetails.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.assistant.SuggestedActivity
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroLabelStyle
import com.example.planyourtrip.ui.tripdetails.components.TagPill
import com.example.planyourtrip.ui.tripdetails.components.TimelineRow

/**
 * "SUGGESTED PLAN" card under an assistant answer: day and date, title, a timeline of the suggestions (each
 * with a checkbox, checked at first) and "Add to Day N", which becomes a disabled "Added" once added. A day
 * outside the trip gets a short note instead of the button.
 */
@Composable
fun SuggestedPlanCard(
    card: PlanCardState,
    onToggle: (Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = card.plan
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        shape = MaterialTheme.shapes.large,
        color = cardColor,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.assistant_plan_label),
                style = HeroLabelStyle,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (card.withinTrip) {
                    stringResource(R.string.assistant_plan_day, card.dayNumber, TripFormatting.weekdayDayMonth(plan.date))
                } else {
                    TripFormatting.date(plan.date)
                },
                style = MaterialTheme.typography.labelLarge,
                color = muted,
            )
            if (plan.title.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(16.dp))
            val selectable = card.withinTrip && card.status == AddStatus.NEW
            plan.activities.forEachIndexed { index, suggestion ->
                TimelineRow(
                    isFirst = index == 0,
                    isLast = index == plan.activities.lastIndex,
                    dotColor = ExtendedTheme.colors.activityType(suggestion.type).content,
                    dotBackground = cardColor,
                    dotTop = 24.dp,
                ) {
                    SuggestionRow(
                        suggestion = suggestion,
                        checked = index in card.checked,
                        enabled = selectable,
                        onToggle = { onToggle(index) },
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            if (card.withinTrip) {
                val added = card.status == AddStatus.ADDED
                PrimaryButton(
                    text = if (added) {
                        stringResource(R.string.assistant_plan_added)
                    } else {
                        stringResource(R.string.assistant_plan_add, card.dayNumber)
                    },
                    onClick = onAdd,
                    enabled = card.canAdd,
                    loading = card.status == AddStatus.ADDING,
                    leadingIcon = if (added) R.drawable.ic_check else R.drawable.ic_add,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = stringResource(R.string.assistant_plan_outside, TripFormatting.date(plan.date)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
            }
        }
    }
}

/** Time, cost, title, type label, venue and note, with a checkbox on the right; the whole row toggles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionRow(
    suggestion: SuggestedActivity,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val typeColor = ExtendedTheme.colors.activityType(suggestion.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Text(
                text = suggestion.startTime?.let { TripFormatting.time(it) }
                    ?: stringResource(R.string.assistant_plan_no_time),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            suggestion.note?.let { note ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TagPill(stringResource(suggestion.type.labelRes()), typeColor.container, typeColor.content)
                suggestion.estimatedCostAgorot?.let { cost ->
                    TagPill(
                        stringResource(R.string.assistant_plan_cost, shekels(cost)),
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.onSurface,
                    )
                }
                suggestion.place?.let { place ->
                    Text(
                        text = place,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        // The row handles the click, so the checkbox itself is not a second touch target.
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(12.dp))
    }
}
