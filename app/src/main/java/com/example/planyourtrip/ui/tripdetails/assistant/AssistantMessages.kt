package com.example.planyourtrip.ui.tripdetails.assistant

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.assistant.AssistantError
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing

/** Shown while the conversation is empty: a short intro and the suggestion chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Intro(chips: List<AssistantChip>, enabled: Boolean, onChip: (String) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AssistantAvatar()
            Spacer(Modifier.width(Spacing.m))
            Text(
                text = stringResource(R.string.assistant_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Spacing.l))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            chips.forEach { chip ->
                val label = chip.label()
                SuggestionChip(
                    onClick = { onChip(label) },
                    label = { Text(label) },
                    enabled = enabled,
                    icon = {
                        Icon(
                            painterResource(R.drawable.ic_sparkle),
                            contentDescription = null,
                            modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                        )
                    },
                    shape = CircleShape,
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        labelColor = MaterialTheme.colorScheme.onPrimary,
                        iconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    border = null,
                )
            }
        }
    }
}

@Composable
private fun AssistantChip.label(): String = when (this) {
    is AssistantChip.PlanDay -> stringResource(R.string.assistant_chip_plan_day, day)
    is AssistantChip.DinnerIdeas -> stringResource(R.string.assistant_chip_dinner, day)
    AssistantChip.BudgetCheck -> stringResource(R.string.assistant_chip_budget)
}

/** The user's message: a teal bubble on the right. */
@Composable
internal fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = BubbleCorner, topEnd = BubbleCorner, bottomStart = BubbleCorner, bottomEnd = BubbleTail),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .padding(start = UserBubbleIndent)
                .weight(1f, fill = false),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
        }
    }
}

/** Assistant answer on the left: avatar, text bubble, optional plan card and the "AI generated" line. */
@Composable
internal fun AssistantAnswer(message: ChatMessage.Assistant, onToggle: (Int) -> Unit, onAdd: () -> Unit) {
    AssistantRow {
        if (message.text.isNotEmpty()) {
            AssistantBubble {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                )
            }
        }
        message.plan?.let { card ->
            Spacer(Modifier.height(Spacing.m))
            SuggestedPlanCard(card, onToggle = onToggle, onAdd = onAdd)
        }
        Spacer(Modifier.height(Spacing.xsPlus))
        Text(
            text = stringResource(R.string.assistant_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

@Composable
private fun AssistantRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        AssistantAvatar()
        Spacer(Modifier.width(Spacing.s))
        Column(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun AssistantBubble(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(topStart = BubbleTail, topEnd = BubbleCorner, bottomStart = BubbleCorner, bottomEnd = BubbleCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = Dimens.CardElevation,
    ) { content() }
}

/** Round teal circle with the sparkle, as in the design. */
@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(AssistantAvatarSize)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_sparkle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(IconSize.m),
        )
    }
}

/** Three pulsing dots in an assistant bubble while a request runs. */
@Composable
internal fun TypingIndicator() {
    val description = stringResource(R.string.assistant_typing)
    val transition = rememberInfiniteTransition(label = "typing")
    AssistantRow {
        AssistantBubble {
            Row(
                modifier = Modifier
                    .padding(horizontal = Spacing.lPlus, vertical = Spacing.l)
                    .semantics { contentDescription = description },
                horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
            ) {
                repeat(3) { index ->
                    val alpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 500),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 160),
                        ),
                        label = "dot$index",
                    )
                    Box(
                        Modifier
                            .size(Dimens.Dot)
                            .alpha(alpha)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ErrorMessage(error: AssistantError, onRetry: () -> Unit) {
    AssistantRow {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Column(Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.m, bottom = Spacing.xs)) {
                Text(
                    text = stringResource(error.messageRes()),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = Spacing.s),
                )
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.assistant_try_again))
                }
            }
        }
    }
}

private fun AssistantError.messageRes(): Int = when (this) {
    AssistantError.NO_CONNECTION -> R.string.assistant_error_no_connection
    AssistantError.RATE_LIMIT -> R.string.assistant_error_rate_limit
    AssistantError.BUSY -> R.string.assistant_error_busy
    AssistantError.APP_CHECK -> R.string.assistant_error_app_check
    AssistantError.UNREADABLE -> R.string.assistant_error_unreadable
    AssistantError.UNKNOWN -> R.string.assistant_error_unknown
}

/** Chat bubbles: the round corners, and the small corner next to the speaker. */
private val BubbleCorner = 20.dp
private val BubbleTail = 6.dp

/** My messages keep this much room on their start side. */
private val UserBubbleIndent = 48.dp

private val AssistantAvatarSize = 36.dp
