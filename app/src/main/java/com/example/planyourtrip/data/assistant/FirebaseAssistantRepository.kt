package com.example.planyourtrip.data.assistant

import com.example.planyourtrip.AppLog
import com.example.planyourtrip.data.trip.ActivityType
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Gemini through Firebase AI Logic (Gemini Developer API backend). The only code that imports Firebase AI.
 * No API key: requests go through the Firebase project in google-services.json, protected by App Check.
 */
class FirebaseAssistantRepository(private val ai: FirebaseAI) : AssistantRepository {

    override suspend fun send(request: AssistantRequest): AssistantResponse = try {
        // Built per request, because the system instruction carries the current trip context.
        val model = ai.generativeModel(
            modelName = request.model.modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = replySchema
            },
            systemInstruction = content { text(assistantInstructions(request.tripContext)) },
        )
        val contents = request.history.map { turn ->
            content(role = if (turn.fromUser) "user" else "model") { text(turn.text) }
        } + content(role = "user") { text(request.message) }
        val text = model.generateContent(contents).text
        when {
            text.isNullOrBlank() -> {
                AppLog.w(TAG, "Assistant answer had no text (UNREADABLE)")
                AssistantResponse.Failed(AssistantError.UNREADABLE)
            }
            else -> {
                // The ViewModel reads it; log what the model sent when it can't, so the cause is visible.
                if (AssistantJson.parseReply(text) == null) AppLog.w(TAG, "Assistant answer is not readable JSON: $text")
                AssistantResponse.Answer(text)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val error = errorFor(e)
        AppLog.w(TAG, "Assistant request to ${request.model.modelName} failed ($error)", e)
        AssistantResponse.Failed(error)
    }

    private fun errorFor(e: Exception): AssistantError = when {
        e is QuotaExceededException -> AssistantError.RATE_LIMIT
        e is RequestTimeoutException || e.causes().any { it is IOException } -> AssistantError.NO_CONNECTION
        e is ResponseStoppedException || e is SerializationException ||
            e is ContentBlockedException || e is PromptBlockedException -> AssistantError.UNREADABLE
        // ServerException (App Check rejections, rate limits) and anything else: read the backend's message.
        else -> classifyServerMessage(e.message)
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }.take(10)

    private companion object {
        const val TAG = "TripAssistant"

        /** `{reply, plan?: {date, title, activities: [{time, title, type, note, place?, costShekels?}]}}`. */
        val replySchema: Schema = Schema.obj(
            properties = mapOf(
                "reply" to Schema.string(),
                "plan" to Schema.obj(
                    properties = mapOf(
                        "date" to Schema.string(description = "Trip day, yyyy-MM-dd"),
                        "title" to Schema.string(),
                        "activities" to Schema.array(
                            Schema.obj(
                                properties = mapOf(
                                    "time" to Schema.string(description = "Start time, 24-hour HH:mm"),
                                    "title" to Schema.string(),
                                    "type" to Schema.enumeration(ActivityType.entries.map { it.name }),
                                    "note" to Schema.string(),
                                    "place" to Schema.string(
                                        description = "The one venue's exact name as on a map, e.g. Colosseum",
                                    ),
                                    "costShekels" to Schema.integer(description = "Estimated cost in ILS"),
                                ),
                                optionalProperties = listOf("place", "costShekels"),
                            ),
                        ),
                    ),
                ),
            ),
            optionalProperties = listOf("plan"),
        )
    }
}
