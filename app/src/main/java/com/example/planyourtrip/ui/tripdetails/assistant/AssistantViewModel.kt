package com.example.planyourtrip.ui.tripdetails.assistant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.assistant.AssistantError
import com.example.planyourtrip.data.assistant.AssistantJson
import com.example.planyourtrip.data.assistant.AssistantModel
import com.example.planyourtrip.data.assistant.AssistantRepository
import com.example.planyourtrip.data.assistant.AssistantRequest
import com.example.planyourtrip.data.assistant.AssistantResponse
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.trip.ActivityRepository
import com.example.planyourtrip.data.trip.ExpenseRepository
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.navigation.TripDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * The trip assistant chat. Scoped to the trip screen's back stack entry (see AppNavHost), so the conversation
 * stays in memory while the trip screen is open and is never stored.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModel(
    private val tripId: Long,
    tripRepository: TripRepository,
    expenseRepository: ExpenseRepository,
    private val activityRepository: ActivityRepository,
    private val authRepository: AuthRepository,
    private val assistantRepository: AssistantRepository,
    private val clock: Clock,
) : ViewModel() {

    private data class TripData(val trip: Trip, val spentAgorot: Long, val activities: List<TripActivity>)

    /** Kept up to date eagerly, so every request is sent with the latest plan and budget. */
    private val tripData: StateFlow<TripData?> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(null)
            combine(
                tripRepository.observeTrip(tripId, user.uid),
                expenseRepository.observeExpenses(tripId, user.uid),
                activityRepository.observeActivities(tripId, user.uid),
            ) { trip, expenses, activities ->
                trip?.let { TripData(it, expenses.sumOf { e -> e.amountAgorot }, activities) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private data class Conversation(
        val messages: List<ChatMessage> = emptyList(),
        val isSending: Boolean = false,
        val error: AssistantError? = null,
        /** What "Try again" resends. */
        val lastMessage: String? = null,
    )

    private val conversation = MutableStateFlow(Conversation())
    private var nextId = 1L

    val uiState: StateFlow<AssistantUiState> = combine(tripData, conversation) { data, chat ->
        val trip = data?.trip
        AssistantUiState(
            trip = trip,
            // Day numbers and "within the trip" follow the current dates, even if they were edited meanwhile.
            messages = if (trip == null) chat.messages else chat.messages.map { message ->
                if (message is ChatMessage.Assistant && message.plan != null) {
                    message.copy(plan = planCard(message.plan, trip.startDate, trip.endDate))
                } else {
                    message
                }
            },
            isSending = chat.isSending,
            error = chat.error,
            chips = if (data == null) emptyList() else suggestionChips(
                data.trip.startDate, data.trip.endDate, data.activities, LocalDate.now(clock),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState())

    /**
     * Sends [text] (a typed message or a suggestion chip). Returns false and does nothing while a request runs,
     * for blank text, or before the trip is loaded; the field is cleared only when this returns true.
     */
    fun onSend(text: String): Boolean {
        val message = text.trim()
        if (message.isEmpty() || conversation.value.isSending || tripData.value == null) return false
        conversation.update {
            it.copy(
                messages = it.messages + ChatMessage.User(nextId++, message),
                isSending = true,
                error = null,
                lastMessage = message,
            )
        }
        request(message)
        return true
    }

    /** "Try again": resends the last message, without a second bubble. */
    fun onRetry() {
        val chat = conversation.value
        val message = chat.lastMessage
        if (chat.isSending || chat.error == null || message == null || tripData.value == null) return
        conversation.update { it.copy(isSending = true, error = null) }
        request(message)
    }

    private fun request(message: String) {
        val data = checkNotNull(tripData.value)
        val request = AssistantRequest(
            tripContext = buildTripContext(data.trip, data.activities, data.spentAgorot, LocalDate.now(clock)),
            // The pending message has no answer yet, so it is not part of the history.
            history = chatHistory(conversation.value.messages),
            message = message,
        )
        viewModelScope.launch {
            val first = assistantRepository.send(request)
            // Overloaded (503) or out of quota: each model has its own capacity and quota, so try the other one
            // once (still "typing") before showing an error.
            val response = if ((first as? AssistantResponse.Failed)?.error in SWITCH_MODEL_ERRORS) {
                assistantRepository.send(request.copy(model = request.model.other))
            } else {
                first
            }
            val reply = (response as? AssistantResponse.Answer)?.let { AssistantJson.parseReply(it.json) }
            conversation.update { chat ->
                when {
                    response is AssistantResponse.Failed -> chat.copy(isSending = false, error = response.error)
                    reply == null -> chat.copy(isSending = false, error = AssistantError.UNREADABLE)
                    else -> chat.copy(
                        messages = chat.messages + ChatMessage.Assistant(
                            id = nextId++,
                            text = reply.text,
                            raw = (response as AssistantResponse.Answer).json,
                            plan = reply.plan?.let { PlanCardState(it) },
                        ),
                        isSending = false,
                    )
                }
            }
        }
    }

    /** Checks or unchecks one suggestion of a plan that was not added yet. */
    fun onToggleSuggestion(messageId: Long, index: Int) = updatePlan(messageId) { card ->
        if (card.status != AddStatus.NEW) return@updatePlan card
        card.copy(checked = if (index in card.checked) card.checked - index else card.checked + index)
    }

    /** "Add to Day N": adds the checked suggestions to the itinerary; the Plan tab and Overview follow Room. */
    fun onAddPlan(messageId: Long) {
        val ownerId = authRepository.currentUser.value?.uid ?: return
        val trip = tripData.value?.trip ?: return
        val card = conversation.value.messages
            .filterIsInstance<ChatMessage.Assistant>()
            .find { it.id == messageId }
            ?.plan
            ?.let { planCard(it, trip.startDate, trip.endDate) }
            ?: return
        if (!card.canAdd) return
        val inputs = suggestionInputs(card.plan, card.checked, trip.startDate, trip.endDate)
        updatePlan(messageId) { it.copy(status = AddStatus.ADDING) }
        viewModelScope.launch {
            val ids = inputs.map { input -> activityRepository.addActivity(tripId, ownerId, input) }
            val added = ids.all { it != null }
            updatePlan(messageId) { it.copy(status = if (added) AddStatus.ADDED else AddStatus.NEW) }
        }
    }

    private fun updatePlan(messageId: Long, change: (PlanCardState) -> PlanCardState) = conversation.update { chat ->
        chat.copy(
            messages = chat.messages.map { message ->
                if (message is ChatMessage.Assistant && message.id == messageId && message.plan != null) {
                    message.copy(plan = change(message.plan))
                } else {
                    message
                }
            },
        )
    }

    companion object {
        /** Failures retried once with the [AssistantModel.other] model. */
        private val SWITCH_MODEL_ERRORS = setOf(AssistantError.BUSY, AssistantError.RATE_LIMIT)

        /** Created with the trip screen's back stack entry as owner, so its route is [TripDetails]. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                val container = appContainer()
                AssistantViewModel(
                    tripId = handle.toRoute<TripDetails>().tripId,
                    tripRepository = container.tripRepository,
                    expenseRepository = container.expenseRepository,
                    activityRepository = container.activityRepository,
                    authRepository = container.authRepository,
                    assistantRepository = container.assistantRepository,
                    clock = container.clock,
                )
            }
        }
    }
}
