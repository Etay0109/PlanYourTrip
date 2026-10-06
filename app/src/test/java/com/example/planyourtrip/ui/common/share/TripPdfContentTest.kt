package com.example.planyourtrip.ui.common.share

import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.data.trip.TripColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TripPdfContentTest {

    private fun nov(day: Int) = LocalDate.of(2026, 11, day)

    /** 12–14 Nov, 3 days, budget ₪1,000. */
    private val trip = Trip(1, "Rome, Italy", nov(12), nov(14), TripColor.SKY, budgetAgorot = 100_000)

    private fun expense(id: Long, agorot: Long, category: ExpenseCategory) =
        Expense(id, 1, "Expense $id", agorot, category, nov(12))

    private fun activity(id: Long, date: LocalDate, time: LocalTime?, title: String = "Activity $id") =
        TripActivity(
            id, 1, date, time, title, ActivityType.DINING,
            note = "Note $id", location = "Place $id", estimatedCostAgorot = 5_000, booked = true,
        )

    private val expenses = listOf(
        expense(1, 20_000, ExpenseCategory.FOOD),
        expense(2, 30_000, ExpenseCategory.HOTELS),
        expense(3, 5_000, ExpenseCategory.FOOD),
    )

    private val activities = listOf(activity(1, nov(12), LocalTime.of(9, 0)))

    @Test
    fun `header has the destination, dates and inclusive length`() {
        val content = buildTripPdfContent(trip, expenses, activities, includeCosts = true)
        assertEquals("Rome, Italy", content.destination)
        assertEquals(nov(12), content.startDate)
        assertEquals(nov(14), content.endDate)
        assertEquals(3, content.lengthDays)
    }

    @Test
    fun `costs on - total, budget, under budget and categories largest first`() {
        val costs = buildTripPdfContent(trip, expenses, activities, includeCosts = true).costs!!
        assertEquals(55_000L, costs.spentAgorot)
        assertEquals(100_000L, costs.budgetAgorot)
        assertEquals(BudgetDifference.Under(45_000), costs.difference)
        assertEquals(listOf(ExpenseCategory.HOTELS, ExpenseCategory.FOOD), costs.categories.map { it.category })
        assertEquals(listOf(30_000L, 25_000L), costs.categories.map { it.amountAgorot })
        assertEquals(30_000 * 100.0 / 55_000, costs.categories[0].percent, 1e-9)
    }

    @Test
    fun `costs off - no money information at all, even with expenses and a budget`() {
        val content = buildTripPdfContent(trip, expenses, activities, includeCosts = false)
        assertNull(content.costs)
    }

    @Test
    fun `no budget - only the total spent`() {
        val costs = buildTripPdfContent(trip.copy(budgetAgorot = null), expenses, activities, true).costs!!
        assertEquals(55_000L, costs.spentAgorot)
        assertNull(costs.budgetAgorot)
        assertNull(costs.difference)
        assertEquals(2, costs.categories.size)
    }

    @Test
    fun `over budget`() {
        val costs = buildTripPdfContent(trip.copy(budgetAgorot = 35_000), expenses, activities, true).costs!!
        assertEquals(BudgetDifference.Over(20_000), costs.difference)
    }

    @Test
    fun `exactly on budget`() {
        val costs = buildTripPdfContent(trip.copy(budgetAgorot = 55_000), expenses, activities, true).costs!!
        assertEquals(BudgetDifference.OnBudget, costs.difference)
    }

    @Test
    fun `no expenses - no cost section, even with a budget`() {
        assertNull(buildTripPdfContent(trip, emptyList(), activities, includeCosts = true).costs)
    }

    @Test
    fun `no activities - no day by day section`() {
        assertNull(buildTripPdfContent(trip, expenses, emptyList(), includeCosts = true).days)
    }

    @Test
    fun `only activities outside the trip dates - no day by day section`() {
        val outside = listOf(activity(1, nov(11), LocalTime.of(9, 0)), activity(2, nov(20), null))
        assertNull(buildTripPdfContent(trip, expenses, outside, includeCosts = true).days)
    }

    @Test
    fun `one section per trip day, a day with nothing planned has no activities`() {
        val days = buildTripPdfContent(trip, expenses, activities, includeCosts = true).days!!
        assertEquals(listOf(1, 2, 3), days.map { it.number })
        assertEquals(listOf(nov(12), nov(13), nov(14)), days.map { it.date })
        assertEquals(1, days[0].activities.size)
        assertTrue(days[1].activities.isEmpty())
        assertTrue(days[2].activities.isEmpty())
    }

    @Test
    fun `activities in time order with untimed ones last, outside the dates left out`() {
        val list = listOf(
            activity(1, nov(13), null, "Untimed"),
            activity(2, nov(13), LocalTime.of(18, 30), "Dinner"),
            activity(3, nov(13), LocalTime.of(9, 0), "Museum"),
            activity(4, nov(20), LocalTime.of(10, 0), "After the trip"),
        )
        val days = buildTripPdfContent(trip, expenses, list, includeCosts = false).days!!
        assertEquals(listOf("Museum", "Dinner", "Untimed"), days[1].activities.map { it.title })
        assertNull(days[1].activities[2].time)
        assertTrue(days.flatMap { it.activities }.none { it.title == "After the trip" })
    }

    @Test
    fun `an activity keeps time, title, type, location and note`() {
        val day = buildTripPdfContent(trip, expenses, activities, includeCosts = true).days!![0]
        assertEquals(
            PdfActivity(LocalTime.of(9, 0), "Activity 1", ActivityType.DINING, "Place 1", "Note 1"),
            day.activities.single(),
        )
    }

    @Test
    fun `blank location and note are left out`() {
        val blank = listOf(activity(1, nov(12), null).copy(location = "  ", note = ""))
        val pdfActivity = buildTripPdfContent(trip, expenses, blank, includeCosts = true).days!![0].activities.single()
        assertNull(pdfActivity.location)
        assertNull(pdfActivity.note)
    }

    @Test
    fun `one-day trip`() {
        val oneDay = trip.copy(endDate = nov(12))
        val content = buildTripPdfContent(oneDay, expenses, activities, includeCosts = true)
        assertEquals(1, content.lengthDays)
        assertNotNull(content.days)
        assertEquals(1, content.days!!.size)
    }
}
