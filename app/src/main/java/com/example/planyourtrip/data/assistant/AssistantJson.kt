package com.example.planyourtrip.data.assistant

import com.example.planyourtrip.data.trip.ActivityType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalTime

/** The assistant's answer: text, plus a suggested day plan when it proposed one. */
data class AssistantReply(val text: String, val plan: SuggestedPlan?)

data class SuggestedPlan(
    val date: LocalDate,
    val title: String,
    val activities: List<SuggestedActivity>,
)

data class SuggestedActivity(
    /** Null when the model's time was not a valid 24-hour time. */
    val startTime: LocalTime?,
    val title: String,
    val type: ActivityType,
    val note: String?,
    val estimatedCostAgorot: Long?,
)

/**
 * Reads the model's structured answer (pure, unit-tested). Lenient: bad parts are dropped instead of failing,
 * and only an answer with neither text nor plan, or text that is not JSON, counts as unreadable.
 */
object AssistantJson {

    /** Same limit as typed amounts (`Money.MAX_AGOROT`, ₪1,000,000,000). */
    private const val MAX_COST_AGOROT = 100_000_000_000L

    private val TIME = Regex("""(\d{1,2}):(\d{2})(:\d{2})?""")

    /** Null when the answer could not be read. */
    fun parseReply(raw: String): AssistantReply? {
        val root = runCatching { Json.parseToJsonElement(stripFence(raw)) }.getOrNull() as? JsonObject ?: return null
        val text = root.string("reply").orEmpty()
        val plan = (root["plan"] as? JsonObject)?.let(::parsePlan)
        if (text.isEmpty() && plan == null) return null
        return AssistantReply(text, plan)
    }

    /** The model sometimes wraps JSON in a ```json … ``` fence. */
    private fun stripFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed.removePrefix("```").removePrefix("json").removeSuffix("```").trim()
    }

    /** Null without a valid date or without any usable activity. */
    private fun parsePlan(plan: JsonObject): SuggestedPlan? {
        val date = plan.string("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        val activities = (plan["activities"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.let(::parseActivity) }
        if (activities.isEmpty()) return null
        return SuggestedPlan(date, plan.string("title").orEmpty(), activities)
    }

    private fun parseActivity(item: JsonObject): SuggestedActivity? {
        val title = item.string("title") ?: return null
        return SuggestedActivity(
            startTime = item.string("time")?.let(::parseTime),
            title = title,
            type = parseType(item.string("type")),
            note = item.string("note"),
            estimatedCostAgorot = item.number("costShekels")?.let(::shekelsToAgorot),
        )
    }

    /** "9:30", "09:30" or "09:30:00" in 0..23 / 0..59; anything else ("9:30 PM", "25:00") is null. */
    fun parseTime(text: String): LocalTime? {
        val match = TIME.matchEntire(text.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        return if (hour in 0..23 && minute in 0..59) LocalTime.of(hour, minute) else null
    }

    /** Unknown or missing types fall back to [ActivityType.OTHER], like stored activities. */
    fun parseType(name: String?): ActivityType =
        ActivityType.entries.find { it.name.equals(name?.trim(), ignoreCase = true) } ?: ActivityType.OTHER

    /** Shekels (possibly with decimals) to whole agorot, HALF_UP; null when ≤ 0 or too large. */
    private fun shekelsToAgorot(shekels: BigDecimal): Long? {
        val agorot = shekels.movePointRight(2).setScale(0, RoundingMode.HALF_UP)
        if (agorot.signum() <= 0 || agorot > BigDecimal.valueOf(MAX_COST_AGOROT)) return null
        return agorot.longValueExact()
    }

    /** Trimmed text of a string field; null when missing, not a string, or blank. */
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf { it.isNotEmpty() }

    /** A number field (or a number written as a string), read exactly. */
    private fun JsonObject.number(key: String): BigDecimal? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content.trim().toBigDecimalOrNull()
    }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
