package com.example.planyourtrip.data.assistant

/** The AI trip assistant. The only implementation that talks to Gemini is [FirebaseAssistantRepository]. */
interface AssistantRepository {
    /** Sends one message with the trip context and the earlier turns. Never throws (except cancellation). */
    suspend fun send(request: AssistantRequest): AssistantResponse
}

data class AssistantRequest(
    /** Fresh description of the trip (dates, plan per day, currency, budget), sent with every request. */
    val tripContext: String,
    /** Earlier answered turns, oldest first. */
    val history: List<ChatTurn>,
    val message: String,
    /** Which model answers; the ViewModel switches to the other one after BUSY or RATE_LIMIT. */
    val model: AssistantModel = AssistantModel.PRIMARY,
)

/** One earlier turn. An assistant turn's [text] is the raw JSON it answered with. */
data class ChatTurn(val fromUser: Boolean, val text: String)

sealed interface AssistantResponse {
    /** The model's answer: JSON text, read with [AssistantJson.parseReply]. */
    data class Answer(val json: String) : AssistantResponse

    data class Failed(val error: AssistantError) : AssistantResponse
}

/** Why a request failed; the UI maps each one to a message with "Try again". */
enum class AssistantError {
    NO_CONNECTION,

    /** Too many requests or the quota is used up (the free tier has a daily request limit). */
    RATE_LIMIT,

    /** The model is overloaded (HTTP 503 "high demand"); temporary. */
    BUSY,
    APP_CHECK,
    UNREADABLE,
    UNKNOWN,
}
