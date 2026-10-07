package com.example.planyourtrip.ui.tripdetails.prep

import com.example.planyourtrip.data.trip.ChecklistItem
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.ChecklistKind.BEFORE_TRIP
import com.example.planyourtrip.data.trip.ChecklistKind.PACKING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrepProgressTest {
    private val today = LocalDate.of(2026, 10, 7)
    private val tripStart = LocalDate.of(2026, 10, 19)

    private var nextId = 1L
    private fun item(
        kind: ChecklistKind = BEFORE_TRIP,
        checked: Boolean = false,
        due: LocalDate? = null,
        createdAt: Long = nextId,
    ) = ChecklistItem(nextId, 1, kind, "Item $nextId", checked, due, createdAt).also { nextId++ }

    @Test
    fun noItems_isEmpty() {
        val progress = prepProgress(emptyList())
        assertEquals(PrepProgress(0, 0), progress)
        assertEquals(0, progress.percent)
        assertEquals(0f, progress.fraction)
        assertEquals(ProgressMessage.Empty, progressMessage(progress, tripStart, today))
        assertEquals(ListCount(0, 0), listCount(emptyList(), BEFORE_TRIP))
    }

    @Test
    fun allDone_is100Percent() {
        val items = listOf(item(checked = true), item(PACKING, checked = true))
        val progress = prepProgress(items)
        assertEquals(2, progress.done)
        assertEquals(0, progress.left)
        assertEquals(100, progress.percent)
        assertEquals(1f, progress.fraction)
        assertEquals(ProgressMessage.AllDone, progressMessage(progress, tripStart, today))
    }

    @Test
    fun countsBothLists() {
        // 5 of 8 before the trip, 7 of 12 packing → 12 of 20, 60%, 8 left.
        val items = List(8) { item(BEFORE_TRIP, checked = it < 5) } + List(12) { item(PACKING, checked = it < 7) }
        val progress = prepProgress(items)
        assertEquals(PrepProgress(12, 20), progress)
        assertEquals(60, progress.percent)
        assertEquals(8, progress.left)
        assertEquals(ListCount(5, 8), listCount(items, BEFORE_TRIP))
        assertEquals(ListCount(7, 12), listCount(items, PACKING))
        assertEquals(ProgressMessage.LeftBeforeTrip(8), progressMessage(progress, tripStart, today))
    }

    @Test
    fun percent_roundsDown_soOnlyAllDoneIs100() {
        assertEquals(95, PrepProgress(19, 20).percent)
        assertEquals(99, PrepProgress(199, 200).percent)
        assertEquals(33, PrepProgress(1, 3).percent)
    }

    @Test
    fun message_afterTheTripStarted_dropsBeforeYourTrip() {
        val progress = PrepProgress(1, 3)
        assertEquals(ProgressMessage.Left(2), progressMessage(progress, tripStart, tripStart))
        assertEquals(ProgressMessage.Left(2), progressMessage(progress, tripStart, tripStart.plusDays(10)))
        assertEquals(ProgressMessage.LeftBeforeTrip(2), progressMessage(progress, tripStart, tripStart.minusDays(1)))
    }

    @Test
    fun overdueItem() {
        assertEquals(DueState.OVERDUE, dueState(item(due = today.minusDays(1)), today))
    }

    @Test
    fun dueToday_isNotOverdue() {
        assertEquals(DueState.UPCOMING, dueState(item(due = today), today))
        assertEquals(DueState.UPCOMING, dueState(item(due = today.plusDays(3)), today))
    }

    @Test
    fun checkedItem_isNeverOverdue() {
        assertEquals(DueState.UPCOMING, dueState(item(checked = true, due = today.minusDays(5)), today))
    }

    @Test
    fun noDueDate() {
        assertEquals(DueState.NONE, dueState(item(), today))
        assertEquals(DueState.NONE, dueState(item(PACKING), today))
    }

    @Test
    fun toggling_changesCountsAndProgress() {
        val items = listOf(item(), item(), item(PACKING))
        assertEquals(PrepProgress(0, 3), prepProgress(items))
        val toggled = items.map { if (it.id == items[1].id) it.copy(checked = true) else it }
        assertEquals(PrepProgress(1, 3), prepProgress(toggled))
        assertEquals(ListCount(1, 2), listCount(toggled, BEFORE_TRIP))
        assertEquals(ListCount(0, 1), listCount(toggled, PACKING))
        // Toggling back.
        assertEquals(PrepProgress(0, 3), prepProgress(toggled.map { it.copy(checked = false) }))
    }

    @Test
    fun itemsOf_keepsTheOrderTheyWereAdded() {
        val late = item(createdAt = 50)
        val early = item(createdAt = 10)
        val packing = item(PACKING, createdAt = 5)
        val sameTimeA = item(createdAt = 20)
        val sameTimeB = item(createdAt = 20)
        val list = itemsOf(listOf(late, packing, sameTimeB, early, sameTimeA), BEFORE_TRIP)
        assertEquals(listOf(early, sameTimeA, sameTimeB, late), list)
    }

    @Test
    fun suggestedItems_onlyForAnEmptyList() {
        val suggestions = listOf("Passport", " Travel insurance ", "", "Online check-in")
        assertEquals(
            listOf("Passport", "Travel insurance", "Online check-in"),
            suggestionsToAdd(emptyList(), BEFORE_TRIP, suggestions),
        )
        val packingOnly = listOf(item(PACKING))
        assertEquals(3, suggestionsToAdd(packingOnly, BEFORE_TRIP, suggestions).size)
        assertTrue(suggestionsToAdd(packingOnly, PACKING, suggestions).isEmpty())
    }
}
