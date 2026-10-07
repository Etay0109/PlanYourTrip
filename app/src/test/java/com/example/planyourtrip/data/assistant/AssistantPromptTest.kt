package com.example.planyourtrip.data.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPromptTest {

    @Test
    fun instructions_containTheRulesAndTheContext() {
        val text = assistantInstructions("Destination: Rome, Italy")
        assertTrue(text.endsWith("Destination: Rome, Italy"))
        assertTrue("same language" in text)
        assertTrue("remaining budget" in text)
        assertTrue("Do not repeat activities that are already planned" in text)
        assertTrue("opening hours" in text && "not sure" in text)
        assertTrue("ACTIVITY, DINING, SHOPPING, LODGING, TRANSPORT, OTHER" in text)
        // One real venue (saved as the location text), never a description of the activity.
        assertTrue("\"place\"" in text && "exactly as it appears on a map" in text && "never a description" in text)
        assertTrue("24-hour HH:mm" in text)
    }

    @Test
    fun serverMessages_areClassified() {
        assertEquals(AssistantError.APP_CHECK, classifyServerMessage("Firebase App Check token is invalid."))
        assertEquals(AssistantError.APP_CHECK, classifyServerMessage("App attestation failed."))
        assertEquals(AssistantError.RATE_LIMIT, classifyServerMessage("Resource has been exhausted (e.g. check quota)."))
        assertEquals(AssistantError.RATE_LIMIT, classifyServerMessage("RESOURCE_EXHAUSTED"))
        assertEquals(AssistantError.RATE_LIMIT, classifyServerMessage("HTTP 429 Too Many Requests"))
        // The real errors seen on the emulator for "Plan day 1 for me" (503) and after the free tier ran out (429).
        assertEquals(
            AssistantError.BUSY,
            classifyServerMessage(
                "This model is currently experiencing high demand. Spikes in demand are usually temporary. " +
                    "Please try again later.",
            ),
        )
        assertEquals(AssistantError.BUSY, classifyServerMessage("503 Service Unavailable"))
        assertEquals(
            AssistantError.RATE_LIMIT,
            classifyServerMessage(
                "* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, " +
                    "limit: 20, model: gemini-3.8-flash\nPlease retry in 17h14m3.059280928s.",
            ),
        )
        assertEquals(AssistantError.UNKNOWN, classifyServerMessage("Internal error"))
        assertEquals(AssistantError.UNKNOWN, classifyServerMessage(null))
    }
}
