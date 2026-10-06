package com.example.planyourtrip.data.assistant

import com.example.planyourtrip.data.trip.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class AssistantJsonTest {

    private fun activity(
        time: String = "09:30",
        title: String = "Vatican Museums",
        type: String = "ACTIVITY",
        note: String = "Book ahead",
        cost: String? = null,
    ) = """{"time":"$time","title":"$title","type":"$type","note":"$note"${cost?.let { ",\"costShekels\":$it" } ?: ""}}"""

    private fun answer(vararg activities: String, date: String = "2026-11-13", reply: String = "Here is day 2.") =
        """{"reply":"$reply","plan":{"date":"$date","title":"Vatican day","activities":[${activities.joinToString(",")}]}}"""

    @Test
    fun replyWithPlan_isRead() {
        val reply = AssistantJson.parseReply(
            answer(
                activity(cost = "90"),
                activity(time = "13:30", title = "Lunch at Borgo Pio", type = "DINING", note = "Cacio e pepe"),
            ),
        )!!
        assertEquals("Here is day 2.", reply.text)
        val plan = reply.plan!!
        assertEquals(LocalDate.of(2026, 11, 13), plan.date)
        assertEquals("Vatican day", plan.title)
        assertEquals(
            listOf(
                SuggestedActivity(LocalTime.of(9, 30), "Vatican Museums", ActivityType.ACTIVITY, "Book ahead", 9_000),
                SuggestedActivity(LocalTime.of(13, 30), "Lunch at Borgo Pio", ActivityType.DINING, "Cacio e pepe", null),
            ),
            plan.activities,
        )
    }

    @Test
    fun replyWithoutPlan_hasNoPlan() {
        val reply = AssistantJson.parseReply("""{"reply":"You have ₪2,500 left."}""")!!
        assertEquals("You have ₪2,500 left.", reply.text)
        assertNull(reply.plan)
    }

    @Test
    fun malformedAnswers_areUnreadable() {
        assertNull(AssistantJson.parseReply("Sure! Here is your plan"))
        assertNull(AssistantJson.parseReply("""{"reply":"cut off"""))
        assertNull(AssistantJson.parseReply("[1, 2]"))
        assertNull(AssistantJson.parseReply(""))
        // Valid JSON, but neither text nor plan.
        assertNull(AssistantJson.parseReply("""{"answer":"hi"}"""))
        assertNull(AssistantJson.parseReply("""{"reply":"   "}"""))
    }

    @Test
    fun codeFence_isStripped() {
        val reply = AssistantJson.parseReply("```json\n{\"reply\":\"Hi\"}\n```")
        assertEquals("Hi", reply?.text)
    }

    @Test
    fun unknownType_fallsBackToOther() {
        val plan = AssistantJson.parseReply(answer(activity(type = "SIGHTSEEING"), activity(type = "dining")))!!.plan!!
        assertEquals(listOf(ActivityType.OTHER, ActivityType.DINING), plan.activities.map { it.type })
    }

    @Test
    fun wrongTimeFormat_keepsTheActivityWithoutATime() {
        val plan = AssistantJson.parseReply(
            answer(activity(time = "9:30 PM"), activity(time = "25:00"), activity(time = "noon"), activity(time = "9:05")),
        )!!.plan!!
        assertEquals(listOf(null, null, null, LocalTime.of(9, 5)), plan.activities.map { it.startTime })
    }

    @Test
    fun parseTime_acceptsOnlyValid24HourTimes() {
        assertEquals(LocalTime.of(0, 0), AssistantJson.parseTime("00:00"))
        assertEquals(LocalTime.of(23, 59), AssistantJson.parseTime("23:59"))
        assertEquals(LocalTime.of(18, 0), AssistantJson.parseTime("18:00:00"))
        assertNull(AssistantJson.parseTime("24:00"))
        assertNull(AssistantJson.parseTime("12:60"))
        assertNull(AssistantJson.parseTime("1230"))
    }

    @Test
    fun costs_areRoundedToAgorot_andInvalidOnesDropped() {
        val plan = AssistantJson.parseReply(
            answer(activity(cost = "12.345"), activity(cost = "-5"), activity(cost = "0"), activity(cost = "\"40\"")),
        )!!.plan!!
        assertEquals(listOf(1_235L, null, null, 4_000L), plan.activities.map { it.estimatedCostAgorot })
    }

    @Test
    fun invalidDate_dropsThePlanButKeepsTheText() {
        val reply = AssistantJson.parseReply(answer(activity(), date = "Day 2"))!!
        assertEquals("Here is day 2.", reply.text)
        assertNull(reply.plan)
    }

    @Test
    fun planWithoutUsableActivities_isDropped() {
        assertNull(AssistantJson.parseReply(answer())!!.plan)
        assertNull(AssistantJson.parseReply(answer(activity(title = " ")))!!.plan)
    }

    @Test
    fun blankTitlesAndNotes_areCleaned() {
        val plan = AssistantJson.parseReply(answer(activity(title = " "), activity(note = " ")))!!.plan!!
        assertEquals(1, plan.activities.size)
        assertNull(plan.activities.single().note)
    }

    @Test
    fun planWithoutReplyText_isStillReadable() {
        val reply = AssistantJson.parseReply(answer(activity(), reply = ""))
        assertNotNull(reply?.plan)
        assertEquals("", reply?.text)
    }
}
