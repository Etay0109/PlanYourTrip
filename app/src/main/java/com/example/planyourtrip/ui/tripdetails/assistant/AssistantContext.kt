package com.example.planyourtrip.ui.tripdetails.assistant

import com.example.planyourtrip.data.assistant.ChatTurn
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.activitiesOn
import com.example.planyourtrip.ui.common.dayNumber
import com.example.planyourtrip.ui.common.isWithinTrip
import com.example.planyourtrip.ui.common.tripDays
import com.example.planyourtrip.ui.tripdetails.defaultDay
import com.example.planyourtrip.ui.tripdetails.outsideTripDates
import com.example.planyourtrip.ui.tripdetails.plannedDays
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// Pure assistant rules (no Android, unit-tested in AssistantContextTest).

/**
 * The trip as plain text for the model, sent with every request: destination, today, dates, the activities
 * already planned on each day, the trip currency and the budget in shekels. Always in English with ISO dates,
 * whatever the device language, so the model reads it the same way.
 */
fun buildTripContext(trip: Trip, activities: List<TripActivity>, spentAgorot: Long, today: LocalDate): String =
    buildString {
        val start = trip.startDate
        val end = trip.endDate
        appendLine("Destination: ${trip.destination}")
        appendLine("Today: ${isoDay(today)}")
        val length = TripFormatting.lengthInDays(start, end)
        val timing = when {
            today.isBefore(start) -> "starts in ${ChronoUnit.DAYS.between(today, start)} days"
            today.isAfter(end) -> "has already ended"
            else -> "is happening now (today is day ${dayNumber(today, start)})"
        }
        appendLine("Dates: ${isoDay(start)} to ${isoDay(end)}, $length ${if (length == 1) "day" else "days"}. The trip $timing.")
        appendLine("Trip currency: ${trip.currencyCode ?: "not chosen"} (all amounts here are in Israeli shekels, ILS)")
        val budget = trip.budgetAgorot
        if (budget == null) {
            appendLine("Budget: no budget set. Spent so far: ${ils(spentAgorot)}.")
        } else {
            val left = budget - spentAgorot
            val remaining = if (left >= 0) "remaining ${ils(left)}" else "over budget by ${ils(-left)} (nothing remaining)"
            appendLine("Budget: total ${ils(budget)}, spent ${ils(spentAgorot)}, $remaining.")
        }
        appendLine("Planned activities per day:")
        tripDays(start, end).forEach { day ->
            val planned = activitiesOn(day, activities)
            val list = if (planned.isEmpty()) "nothing planned" else planned.joinToString("; ") { describe(it) }
            appendLine("- Day ${dayNumber(day, start)}, ${isoDay(day)}: $list")
        }
        val outside = outsideTripDates(activities, start, end)
        if (outside.isNotEmpty()) {
            appendLine("Activities outside the trip dates: " + outside.joinToString("; ") { "${it.date} ${describe(it)}" })
        }
    }.trim()

/** "10:00 Colosseum [ACTIVITY, ~₪90, at Piazza del Colosseo]", "no time: Buy a Roma Pass [OTHER]". */
private fun describe(activity: TripActivity): String {
    val time = activity.startTime?.let { "%02d:%02d".format(Locale.ROOT, it.hour, it.minute) } ?: "no time:"
    val details = listOfNotNull(
        activity.type.name,
        activity.estimatedCostAgorot?.let { "~${ils(it)}" },
        activity.location?.let { "at $it" },
        "booked".takeIf { activity.booked },
    ).joinToString(", ")
    return "$time ${activity.title} [$details]"
}

/** "2026-11-12 (Thu)" */
private fun isoDay(date: LocalDate): String =
    "$date (${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)})"

private fun ils(agorot: Long): String = "₪" + Money.formatAgorot(agorot, Locale.ENGLISH)

/** The suggestion chips shown while the conversation is empty; the UI turns them into text. */
sealed interface AssistantChip {
    /** "Plan day N for me": the first trip day without activities. */
    data class PlanDay(val day: Int) : AssistantChip

    /** "Dinner ideas for day N": the default day (today during the trip, otherwise day 1). */
    data class DinnerIdeas(val day: Int) : AssistantChip

    /** "Am I within budget?" */
    data object BudgetCheck : AssistantChip
}

/** "Plan day N" is left out when every day already has an activity. */
fun suggestionChips(
    start: LocalDate,
    end: LocalDate,
    activities: List<TripActivity>,
    today: LocalDate,
): List<AssistantChip> {
    val planned = plannedDays(start, end, activities)
    val firstOpen = tripDays(start, end).firstOrNull { it !in planned }
    return listOfNotNull(
        firstOpen?.let { AssistantChip.PlanDay(dayNumber(it, start)) },
        AssistantChip.DinnerIdeas(dayNumber(defaultDay(start, end, today), start)),
        AssistantChip.BudgetCheck,
    )
}

/**
 * The earlier turns to send with a new request: user messages that got an answer, and the answers (as the
 * raw JSON the model wrote). A message whose request failed has no answer, so it is never sent twice.
 */
fun chatHistory(messages: List<ChatMessage>): List<ChatTurn> = buildList {
    messages.forEachIndexed { index, message ->
        when (message) {
            is ChatMessage.User ->
                if (messages.getOrNull(index + 1) is ChatMessage.Assistant) add(ChatTurn(fromUser = true, message.text))
            is ChatMessage.Assistant -> add(ChatTurn(fromUser = false, message.raw))
        }
    }
}

/** Whether a suggested plan can be added: its day must be one of the (current) trip days. */
fun planCard(card: PlanCardState, start: LocalDate, end: LocalDate): PlanCardState = card.copy(
    dayNumber = dayNumber(card.plan.date, start),
    withinTrip = isWithinTrip(card.plan.date, start, end),
)
