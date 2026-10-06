package com.example.planyourtrip.ui.tripdetails.assistant

import com.example.planyourtrip.data.assistant.AssistantError
import com.example.planyourtrip.data.assistant.SuggestedPlan
import com.example.planyourtrip.data.trip.Trip

data class AssistantUiState(
    /** Null until the trip is loaded; sending waits for it (the context needs it). */
    val trip: Trip? = null,
    val messages: List<ChatMessage> = emptyList(),
    /** A request is running: the typing indicator shows and the send button is disabled. */
    val isSending: Boolean = false,
    /** The last request failed; shown after the conversation with "Try again". */
    val error: AssistantError? = null,
    /** Shown above the conversation while it is empty. */
    val chips: List<AssistantChip> = emptyList(),
) {
    val isReady: Boolean get() = trip != null
}

sealed interface ChatMessage {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatMessage

    data class Assistant(
        override val id: Long,
        val text: String,
        /** The model's JSON, sent back as history. */
        val raw: String,
        val plan: PlanCardState? = null,
    ) : ChatMessage
}

enum class AddStatus { NEW, ADDING, ADDED }

/** The "SUGGESTED PLAN" card under an answer. */
data class PlanCardState(
    val plan: SuggestedPlan,
    /** Indexes of the suggestions that will be added; all checked at first. */
    val checked: Set<Int> = plan.activities.indices.toSet(),
    val status: AddStatus = AddStatus.NEW,
    /** Day number in the current trip dates (may be ≤ 0 or beyond the trip when [withinTrip] is false). */
    val dayNumber: Int = 0,
    /** False → no "Add to Day N" button. */
    val withinTrip: Boolean = false,
) {
    val canAdd: Boolean get() = withinTrip && status == AddStatus.NEW && checked.isNotEmpty()
}
