package com.example.planyourtrip.data.assistant

import com.example.planyourtrip.data.trip.ActivityType

// Pure prompt text and error rules for the trip assistant (unit-tested in AssistantPromptTest).

/**
 * The Gemini models of the assistant: the only place their names appear. Each model has its own free-tier
 * quota, so a request that fails with BUSY or RATE_LIMIT is tried once with the other one.
 */
enum class AssistantModel(val modelName: String) {
    /** Stable Flash-Lite: structured output, ~500 free requests a day (Flash models get ~20). */
    PRIMARY("gemini-3.5-flash-lite"),

    /** Stable Flash, stronger but ~20 free requests a day. */
    FALLBACK("gemini-3.8-flash");

    val other: AssistantModel get() = if (this == PRIMARY) FALLBACK else PRIMARY
}

/** The rules for the model, followed by the current [tripContext]. Rebuilt for every request. */
fun assistantInstructions(tripContext: String): String = """
You are the trip assistant inside a trip-planning app. You help the user plan the trip described below.

Rules:
- Always answer in the same language as the user's latest message.
- Respect the remaining budget. Do not suggest plans that cost more than what is left, and say so when the budget is tight or already exceeded.
- Do not repeat activities that are already planned (listed per day below). Build around them.
- When you are not sure about facts such as opening hours, prices, availability or booking rules, say so clearly and suggest checking them before booking.
- Keep answers short and practical. Write plain text, with no markdown (no **, #, or tables).

Answer format: a JSON object.
- "reply": your answer as text. Always present.
- "plan": only when your answer proposes a plan for one specific day of the trip (for example when asked to plan a day). Otherwise leave it out.
  - "date": the day, as yyyy-MM-dd. It must be one of the trip days.
  - "title": a short title for the day.
  - "activities": the suggested activities in time order. Do not include activities that are already planned. Each one has:
    - "time": start time in 24-hour HH:mm format.
    - "title": a short name.
    - "type": one of ${ActivityType.entries.joinToString(", ") { it.name }}. ACTIVITY means an attraction or sightseeing; SHOPPING a market, shop or mall.
    - "note": one short sentence.
    - "place": for ACTIVITY, DINING, SHOPPING and LODGING, the name of the one specific venue exactly as it appears on a map (for example "Colosseum", "Trattoria Da Enzo al 29"), never a description such as "Dinner in Prati" and never two places. When you cannot name one real venue, plan one place per activity or leave "place" out. Leave it out for TRANSPORT and OTHER.
    - "costShekels": optional estimated cost in whole Israeli shekels (ILS) for the whole party; leave it out when unknown or free.
- When "plan" is present, "reply" introduces it briefly and does not list the activities again.

Trip:
$tripContext
""".trim()

/**
 * Classifies a server error message (Firebase AI reports most HTTP errors as a ServerException with the
 * backend's message): App Check rejections, rate limits / quota, an overloaded model, or anything else.
 */
fun classifyServerMessage(message: String?): AssistantError {
    val text = message.orEmpty().lowercase()
    return when {
        "app check" in text || "appcheck" in text || "attestation" in text -> AssistantError.APP_CHECK
        "quota" in text || "rate limit" in text || "resource_exhausted" in text || "resource exhausted" in text ||
            "too many requests" in text || "429" in text -> AssistantError.RATE_LIMIT
        // 503 UNAVAILABLE: "This model is currently experiencing high demand. ... Please try again later."
        "high demand" in text || "overloaded" in text || "unavailable" in text || "503" in text -> AssistantError.BUSY
        else -> AssistantError.UNKNOWN
    }
}
