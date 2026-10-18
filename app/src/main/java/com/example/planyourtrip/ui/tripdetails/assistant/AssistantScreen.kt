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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.planyourtrip.R
import com.example.planyourtrip.data.assistant.AssistantError
import com.example.planyourtrip.data.assistant.SuggestedActivity
import com.example.planyourtrip.data.assistant.SuggestedPlan
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.components.BackTopAppBar
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime

/** [viewModel] is scoped to the trip screen (AppNavHost), so the conversation outlives this screen. */
@Composable
fun AssistantScreen(onBack: () -> Unit, viewModel: AssistantViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssistantContent(
        state = state,
        onBack = onBack,
        onSend = viewModel::onSend,
        onRetry = viewModel::onRetry,
        onToggleSuggestion = viewModel::onToggleSuggestion,
        onAddPlan = viewModel::onAddPlan,
    )
}

@Composable
fun AssistantContent(
    state: AssistantUiState,
    onBack: () -> Unit,
    /** Returns true when the message was sent, so the field can be cleared. */
    onSend: (String) -> Boolean,
    onRetry: () -> Unit,
    onToggleSuggestion: (messageId: Long, index: Int) -> Unit,
    onAddPlan: (messageId: Long) -> Unit,
) {
    // Typed text is Compose state: updated synchronously, never round-tripped through a StateFlow.
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val itemCount = 1 + state.messages.size + (if (state.isSending) 1 else 0) + (if (state.error != null) 1 else 0)
    // Follow the conversation: show the newest message, the typing indicator or the error.
    LaunchedEffect(state.messages.size, state.isSending, state.error) {
        if (state.messages.isNotEmpty() || state.isSending) listState.animateScrollToItem(itemCount - 1)
    }
    val canSend = state.isReady && !state.isSending

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopAppBar(onBack = onBack, title = stringResource(R.string.assistant_title)) },
        bottomBar = {
            InputBar(
                text = input,
                onTextChange = { input = it.take(MAX_MESSAGE_CHARS) },
                destination = state.trip?.destination.orEmpty(),
                sendEnabled = canSend && input.isNotBlank(),
                onSend = { if (onSend(input)) input = "" },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item(key = "intro") {
                if (state.messages.isEmpty()) {
                    Intro(state.chips, enabled = canSend, onChip = { onSend(it) })
                }
            }
            items(state.messages, key = { it.id }) { message ->
                when (message) {
                    is ChatMessage.User -> UserBubble(message.text)
                    is ChatMessage.Assistant -> AssistantAnswer(
                        message = message,
                        onToggle = { index -> onToggleSuggestion(message.id, index) },
                        onAdd = { onAddPlan(message.id) },
                    )
                }
            }
            if (state.isSending) {
                item(key = "typing") { TypingIndicator() }
            }
            state.error?.let { error ->
                item(key = "error") { ErrorMessage(error, onRetry) }
            }
        }
    }
}

/** Shown while the conversation is empty: a short intro and the suggestion chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Intro(chips: List<AssistantChip>, enabled: Boolean, onChip: (String) -> Unit) {
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
private fun UserBubble(text: String) {
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
private fun AssistantAnswer(message: ChatMessage.Assistant, onToggle: (Int) -> Unit, onAdd: () -> Unit) {
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
private fun TypingIndicator() {
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
private fun ErrorMessage(error: AssistantError, onRetry: () -> Unit) {
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

/** The message counter shows this many characters before [MAX_MESSAGE_CHARS]. */
private const val CHAR_COUNT_MARGIN = 100

/** Text field and round send button; padded above the navigation bar and the keyboard. */
@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    destination: String,
    sendEnabled: Boolean,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        stringResource(R.string.assistant_input_hint, destination),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // "950 / 1000" only close to the limit, where the field stops taking text.
                supportingText = if (text.length >= MAX_MESSAGE_CHARS - CHAR_COUNT_MARGIN) {
                    { Text(stringResource(R.string.assistant_char_count, text.length, MAX_MESSAGE_CHARS)) }
                } else {
                    null
                },
                maxLines = 4,
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.m))
            FilledIconButton(
                onClick = onSend,
                enabled = sendEnabled,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.size(SendButtonSize),
            ) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.assistant_send))
            }
        }
    }
}

// ---------- Previews (sample data lives only here) ----------

private val previewTrip = Trip(
    id = 1,
    destination = "Rome, Italy",
    startDate = LocalDate.of(2026, 11, 12),
    endDate = LocalDate.of(2026, 11, 17),
    color = TripColor.SKY,
    budgetAgorot = 600_000,
)

private val previewPlan = SuggestedPlan(
    date = LocalDate.of(2026, 11, 13),
    title = "Vatican & Tiber Glow",
    activities = listOf(
        SuggestedActivity(LocalTime.of(9, 30), "Vatican Museums & Sistine Chapel", ActivityType.ACTIVITY,
            "Skip-the-line slot recommended", 9_000),
        SuggestedActivity(LocalTime.of(13, 30), "Lunch at Borgo Pio", ActivityType.DINING,
            "Cacio e pepe in a quiet courtyard", 9_000),
        SuggestedActivity(LocalTime.of(15, 30), "Castel Sant'Angelo & river walk", ActivityType.ACTIVITY,
            "Stroll along Ponte Sant'Angelo with a gelato stop", null),
    ),
)

private val previewConversation = listOf(
    ChatMessage.User(1, "Plan day 2 for me"),
    ChatMessage.Assistant(
        2,
        "Here is a balanced day 2 in the historic center and the Vatican. Opening hours change by season, so check them before booking.",
        raw = "",
        plan = PlanCardState(previewPlan, checked = setOf(0, 1), dayNumber = 2, withinTrip = true),
    ),
)

@Composable
private fun Preview(state: AssistantUiState, darkTheme: Boolean = false) {
    PlanYourTripTheme(darkTheme = darkTheme) {
        AssistantContent(state, onBack = {}, onSend = { true }, onRetry = {}, onToggleSuggestion = { _, _ -> },
            onAddPlan = {})
    }
}

private val previewChips = listOf(
    AssistantChip.PlanDay(2), AssistantChip.DinnerIdeas(1), AssistantChip.BudgetCheck,
)

@Preview(name = "Empty", widthDp = 411, heightDp = 891)
@Composable
internal fun EmptyPreview() = Preview(AssistantUiState(trip = previewTrip, chips = previewChips))

@Preview(name = "Conversation with plan", widthDp = 411, heightDp = 1500)
@Composable
internal fun ConversationPreview() =
    Preview(AssistantUiState(trip = previewTrip, messages = previewConversation))

@Preview(name = "Typing, dark", widthDp = 411, heightDp = 1500)
@Composable
internal fun TypingDarkPreview() = Preview(
    AssistantUiState(
        trip = previewTrip,
        messages = previewConversation + ChatMessage.User(3, "Any dinner ideas near the hotel?"),
        isSending = true,
    ),
    darkTheme = true,
)

@Preview(name = "Added + error", widthDp = 411, heightDp = 1500)
@Composable
internal fun ErrorPreview() = Preview(
    AssistantUiState(
        trip = previewTrip,
        messages = listOf(
            previewConversation[0],
            (previewConversation[1] as ChatMessage.Assistant).let {
                it.copy(plan = it.plan?.copy(status = AddStatus.ADDED))
            },
            ChatMessage.User(3, "Is the Pantheon free?"),
        ),
        error = AssistantError.NO_CONNECTION,
    ),
)

@Preview(name = "Outside the trip, narrow, large font", widthDp = 320, heightDp = 1800, fontScale = 1.5f)
@Composable
internal fun OutsideNarrowPreview() = Preview(
    AssistantUiState(
        trip = previewTrip,
        messages = listOf(
            previewConversation[0],
            (previewConversation[1] as ChatMessage.Assistant).let {
                it.copy(plan = it.plan?.copy(withinTrip = false, dayNumber = 0))
            },
        ),
    ),
)

/** Chat bubbles: the round corners, and the small corner next to the speaker. */
private val BubbleCorner = 20.dp
private val BubbleTail = 6.dp

/** My messages keep this much room on their start side. */
private val UserBubbleIndent = 48.dp

private val AssistantAvatarSize = 36.dp

/** The round Send button. */
private val SendButtonSize = 56.dp
