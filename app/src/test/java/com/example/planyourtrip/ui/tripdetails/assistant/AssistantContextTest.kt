package com.example.planyourtrip.ui.tripdetails.assistant

import com.example.planyourtrip.data.assistant.ChatTurn
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.data.trip.TripColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AssistantContextTest {

    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private val today = LocalDate.of(2026, 10, 5)
    private val rome = Trip(1, "Rome, Italy", nov(12), nov(14), TripColor.SKY)

    private fun activity(id: Long, date: LocalDate, time: LocalTime?, title: String, type: ActivityType = ActivityType.ACTIVITY) =
        TripActivity(id, 1, date, time, title, type)

    // ---------- Context ----------

    @Test
    fun context_listsEveryDayInOrder_withUntimedActivitiesLast() {
        val activities = listOf(
            activity(1, nov(12), null, "Buy a Roma Pass", ActivityType.OTHER),
            activity(2, nov(12), LocalTime.of(10, 0), "Colosseum").copy(estimatedCostAgorot = 9_000, booked = true),
            activity(3, nov(14), LocalTime.of(20, 0), "Dinner in Trastevere", ActivityType.DINING),
        )
        val text = buildTripContext(rome, activities, spentAgorot = 0, today = today)
        assertTrue("Destination: Rome, Italy" in text)
        assertTrue("Today: 2026-10-05 (Mon)" in text)
        assertTrue("Dates: 2026-11-12 (Thu) to 2026-11-14 (Sat), 3 days. The trip starts in 38 days." in text)
        assertTrue(
            "- Day 1, 2026-11-12 (Thu): 10:00 Colosseum [ACTIVITY, ~₪90, booked]; no time: Buy a Roma Pass [OTHER]" in text,
        )
        assertTrue("- Day 2, 2026-11-13 (Fri): nothing planned" in text)
        assertTrue("- Day 3, 2026-11-14 (Sat): 20:00 Dinner in Trastevere [DINING]" in text)
        assertFalse("outside the trip dates" in text)
    }

    @Test
    fun context_mentionsActivitiesOutsideTheDates() {
        val text = buildTripContext(rome, listOf(activity(1, nov(20), null, "Uffizi")), 0, today)
        assertTrue("Activities outside the trip dates: 2026-11-20 no time: Uffizi [ACTIVITY]" in text)
    }

    @Test
    fun context_tripHappeningNow_andEnded() {
        assertTrue("is happening now (today is day 2)" in buildTripContext(rome, emptyList(), 0, nov(13)))
        assertTrue("has already ended" in buildTripContext(rome, emptyList(), 0, nov(20)))
    }

    @Test
    fun context_currency() {
        assertTrue("Trip currency: not chosen" in buildTripContext(rome, emptyList(), 0, today))
        assertTrue("Trip currency: EUR" in buildTripContext(rome.copy(currencyCode = "EUR"), emptyList(), 0, today))
    }

    @Test
    fun context_budget() {
        assertTrue(
            "Budget: no budget set. Spent so far: ₪120." in buildTripContext(rome, emptyList(), 12_000, today),
        )
        val withBudget = rome.copy(budgetAgorot = 600_000)
        assertTrue(
            "Budget: total ₪6,000, spent ₪3,500.50, remaining ₪2,499.50." in
                buildTripContext(withBudget, emptyList(), 350_050, today),
        )
        assertTrue(
            "Budget: total ₪6,000, spent ₪7,000, over budget by ₪1,000 (nothing remaining)." in
                buildTripContext(withBudget, emptyList(), 700_000, today),
        )
    }

    // ---------- Chips ----------

    @Test
    fun chips_planTheFirstOpenDay_dinnerOnTheDefaultDay() {
        val activities = listOf(activity(1, nov(12), null, "Colosseum"))
        assertEquals(
            listOf(AssistantChip.PlanDay(2), AssistantChip.DinnerIdeas(1), AssistantChip.BudgetCheck),
            suggestionChips(nov(12), nov(14), activities, today),
        )
    }

    @Test
    fun chips_duringTheTrip_dinnerIsForToday() {
        assertEquals(
            listOf(AssistantChip.PlanDay(1), AssistantChip.DinnerIdeas(3), AssistantChip.BudgetCheck),
            suggestionChips(nov(12), nov(14), emptyList(), nov(14)),
        )
    }

    @Test
    fun chips_everyDayPlanned_noPlanDayChip() {
        val activities = (12..14).map { activity(it.toLong(), nov(it), null, "Day $it") }
        assertEquals(
            listOf(AssistantChip.DinnerIdeas(1), AssistantChip.BudgetCheck),
            suggestionChips(nov(12), nov(14), activities, today),
        )
    }

    // ---------- History ----------

    @Test
    fun history_skipsAMessageWhoseRequestFailed() {
        val messages = listOf(
            ChatMessage.User(1, "Plan day 2"),
            ChatMessage.Assistant(2, "Here it is", raw = """{"reply":"Here it is"}"""),
            ChatMessage.User(3, "This one failed"),
            ChatMessage.User(4, "Am I within budget?"),
            ChatMessage.Assistant(5, "Yes", raw = """{"reply":"Yes"}"""),
            ChatMessage.User(6, "Pending"),
        )
        assertEquals(
            listOf(
                ChatTurn(true, "Plan day 2"),
                ChatTurn(false, """{"reply":"Here it is"}"""),
                ChatTurn(true, "Am I within budget?"),
                ChatTurn(false, """{"reply":"Yes"}"""),
            ),
            chatHistory(messages),
        )
    }
}
