package com.example.planyourtrip

import com.example.planyourtrip.data.assistant.AssistantRepository
import com.example.planyourtrip.data.assistant.AssistantRequest
import com.example.planyourtrip.data.assistant.AssistantResponse
import kotlinx.coroutines.CompletableDeferred

/**
 * Scripted [AssistantRepository] for unit tests: answers with [responses] in order and records every request.
 * Set [gate] to hold the next request until it is completed (to see the "typing" state).
 */
class FakeAssistantRepository(vararg responses: AssistantResponse) : AssistantRepository {
    val responses = ArrayDeque(responses.toList())
    val requests = mutableListOf<AssistantRequest>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun send(request: AssistantRequest): AssistantResponse {
        requests += request
        gate?.await()
        return checkNotNull(responses.removeFirstOrNull()) { "No scripted response left" }
    }
}
