package com.example.planyourtrip.ui.tripdetails.assistant

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.example.planyourtrip.data.assistant.AssistantError
import com.example.planyourtrip.data.assistant.SuggestedActivity
import com.example.planyourtrip.data.assistant.SuggestedPlan
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import java.time.LocalDate
import java.time.LocalTime

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

private val previewChips = listOf(
    AssistantChip.PlanDay(2), AssistantChip.DinnerIdeas(1), AssistantChip.BudgetCheck,
)

@Composable
private fun Preview(state: AssistantUiState, darkTheme: Boolean = false) {
    PlanYourTripTheme(darkTheme = darkTheme) {
        AssistantContent(state, onBack = {}, onSend = { true }, onRetry = {}, onToggleSuggestion = { _, _ -> },
            onAddPlan = {})
    }
}

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
