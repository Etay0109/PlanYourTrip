package com.example.planyourtrip.data.trip

import com.example.planyourtrip.data.sync.ItemData
import com.example.planyourtrip.data.sync.SyncDocuments
import com.example.planyourtrip.data.sync.SyncTestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate

/** The text limits that keep every write within firestore.rules (see TextLimits.kt and rules-tests). */
class TextLimitsTest {

    private fun x(n: Int) = "x".repeat(n)

    @Test
    fun limitTo_keepsShortText_andCutsLongText() {
        val short = "Rome"
        assertSame(short, short.limitTo(200))
        assertEquals(x(200), x(200).limitTo(200))
        assertEquals(x(200), x(250).limitTo(200))
    }

    @Test
    fun limitTo_neverCutsAnEmojiInHalf() {
        // "😀" is two UTF-16 units: cutting after the first would leave half a character.
        val text = x(199) + "😀"
        assertEquals(x(199), text.limitTo(200))
        assertEquals(text, (text + "!").limitTo(201))
    }

    @Test
    fun activityInput_normalized_cutsTitleNoteAndLocation() {
        val input = ActivityInput(
            date = LocalDate.of(2026, 11, 13),
            startTime = null,
            title = "  ${x(250)}  ",
            type = ActivityType.ACTIVITY,
            note = x(2500),
            location = x(400),
            estimatedCostAgorot = null,
            booked = false,
        ).normalized()

        assertEquals(TextLimits.ACTIVITY_TITLE, input.title.length)
        assertEquals(TextLimits.ACTIVITY_NOTE, input.note!!.length)
        assertEquals(TextLimits.ACTIVITY_LOCATION, input.location!!.length)
        assertNull(input.copy(note = "  ").normalized().note)
    }

    @Test
    fun upload_cutsTextLongerThanTheRulesAllow() {
        val trip = SyncDocuments.toDocument(SyncTestData.trip("t1", updatedAt = 1, destination = x(300)))
        assertEquals(x(TextLimits.DESTINATION), trip["destination"])

        val expense = SyncDocuments.toDocument(SyncTestData.expense("e1", "t1", updatedAt = 1, name = x(300)))
        assertEquals(x(TextLimits.EXPENSE_NAME), expense["name"])

        val activity = SyncTestData.activity("a1", "t1", updatedAt = 1)
        val longActivity = activity.copy(
            data = (activity.data as ItemData.ActivityData).let {
                it.copy(input = it.input.copy(title = x(300), note = x(3000), location = x(500)))
            },
        )
        val activityDoc = SyncDocuments.toDocument(longActivity)
        assertEquals(x(TextLimits.ACTIVITY_TITLE), activityDoc["title"])
        assertEquals(x(TextLimits.ACTIVITY_NOTE), activityDoc["note"])
        assertEquals(x(TextLimits.ACTIVITY_LOCATION), activityDoc["location"])

        val item = SyncDocuments.toDocument(SyncTestData.checklistItem("c1", "t1", updatedAt = 1, text = x(600)))
        assertEquals(x(TextLimits.CHECKLIST_TEXT), item["text"])

        val traveler = SyncDocuments.toDocument(SyncTestData.traveler("r1", "t1", updatedAt = 1, name = x(150)))
        assertEquals(x(TextLimits.TRAVELER_NAME), traveler["name"])
    }

    @Test
    fun upload_keepsTextWithinTheLimitsAsItIs() {
        val doc = SyncDocuments.toDocument(SyncTestData.expense("e1", "t1", updatedAt = 1, name = "Dinner"))
        assertEquals("Dinner", doc["name"])
    }
}
