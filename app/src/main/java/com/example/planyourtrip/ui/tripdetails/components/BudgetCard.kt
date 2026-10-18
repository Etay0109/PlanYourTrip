package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroLabelStyle
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.tripdetails.BudgetSummary

/**
 * "TRIP BUDGET" card shared by Overview and Budget.
 * - No budget ([summary] null): a short message and a "Set budget" button ([onSetBudget]).
 * - With a budget: amount left (or over budget), progress bar, spent with percentage, total, per-day line.
 * Amounts count only my share of each expense. [onClick] makes the whole card tappable (Overview opens the Budget
 * tab). [showEditButton] adds an edit icon next to the total (Budget tab) that calls [onSetBudget].
 */
@Composable
fun BudgetCard(
    summary: BudgetSummary?,
    onSetBudget: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showEditButton: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(Spacing.xxl)) {
            if (summary == null) NoBudget(onSetBudget) else WithBudget(summary, showEditButton, onSetBudget)
        }
    }
    val shape = MaterialTheme.shapes.extraLarge
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    if (onClick != null) {
        Surface(onClick = onClick, shape = shape, color = color, shadowElevation = Dimens.CardElevation, modifier = modifier.fillMaxWidth()) {
            content()
        }
    } else {
        Surface(shape = shape, color = color, shadowElevation = Dimens.CardElevation, modifier = modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun NoBudget(onSetBudget: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            BudgetLabel()
            Spacer(Modifier.height(Spacing.s))
            Text(
                text = stringResource(R.string.budget_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.m))
        IconChip(R.drawable.ic_wallet)
    }
    Spacer(Modifier.height(Spacing.xl))
    PrimaryButton(text = stringResource(R.string.budget_set), onClick = onSetBudget)
}

@Composable
private fun WithBudget(summary: BudgetSummary, showEditButton: Boolean, onEdit: () -> Unit) {
    val colors = ExtendedTheme.colors
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            BudgetLabel()
            Spacer(Modifier.height(Spacing.xs))
            if (summary.isOverBudget) {
                Text(
                    text = stringResource(R.string.budget_over, shekels(-summary.leftAgorot)),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = shekels(summary.leftAgorot),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Spacer(Modifier.width(Spacing.xsPlus))
                    Text(
                        text = stringResource(R.string.budget_left),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal),
                        color = muted,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
        // The Budget tab (with the edit button) is the design's plain card; the Overview keeps the wallet chip.
        if (!showEditButton) {
            Spacer(Modifier.width(Spacing.m))
            IconChip(R.drawable.ic_wallet)
        }
    }

    Spacer(Modifier.height(Spacing.xl))
    LinearProgressIndicator(
        progress = { summary.progress },
        color = if (summary.isWarning) colors.budgetWarning else MaterialTheme.colorScheme.primary,
        trackColor = colors.budgetTrack,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.ProgressBarHeight),
    )
    Spacer(Modifier.height(Spacing.m))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Box(
                Modifier
                    .size(Dimens.Dot)
                    .background(
                        if (summary.isWarning) colors.budgetWarning else MaterialTheme.colorScheme.primary,
                        CircleShape,
                    )
            )
            Text(
                text = stringResource(
                    R.string.budget_spent,
                    shekels(summary.spentAgorot),
                    Money.formatPercent(summary.spentPercent),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(Spacing.s))
        Text(
            text = stringResource(R.string.budget_total, shekels(summary.totalAgorot)),
            style = MaterialTheme.typography.bodyLarge,
            color = muted,
        )
        if (showEditButton) {
            IconButton(onClick = onEdit, modifier = Modifier.size(EditButtonSize)) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit),
                    contentDescription = stringResource(R.string.budget_edit),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.s),
                )
            }
        }
    }

    summary.perDay?.let { perDay ->
        HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.m), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = pluralStringResource(R.plurals.budget_per_day, perDay.days, shekels(perDay.amountAgorot), perDay.days),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = muted,
        )
    }
}

@Composable
private fun BudgetLabel() {
    Text(
        text = stringResource(R.string.budget_label),
        style = HeroLabelStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The pencil button next to the budget. */
private val EditButtonSize = 36.dp
