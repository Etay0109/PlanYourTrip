package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ExpenseCategory.ATTRACTIONS
import com.example.planyourtrip.data.trip.ExpenseCategory.FLIGHTS
import com.example.planyourtrip.data.trip.ExpenseCategory.FOOD
import com.example.planyourtrip.data.trip.ExpenseCategory.HOTELS
import com.example.planyourtrip.ui.common.categoryTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetCalculationsTest {

    // Rome, 12–17 Nov 2026 (6 days); "today" is set per test.
    private val start = LocalDate.of(2026, 11, 12)
    private val end = LocalDate.of(2026, 11, 17)
    private val before = LocalDate.of(2026, 10, 5)

    private var nextId = 1L
    private fun expense(shekels: Long, category: ExpenseCategory = FOOD, agorot: Long = 0) =
        Expense(nextId++, 1, "e", shekels * 100 + agorot, category, start)

    private fun summary(budgetShekels: Long?, expenses: List<Expense>, today: LocalDate, s: LocalDate = start, e: LocalDate = end) =
        budgetSummary(budgetShekels?.times(100), expenses.sumOf { it.amountAgorot }, s, e, today)

    @Test
    fun noBudget_givesNoSummary() {
        assertNull(summary(null, listOf(expense(100)), before))
    }

    @Test
    fun zeroExpenses_everythingLeft() {
        val s = checkNotNull(summary(6_000, emptyList(), before))
        assertEquals(600_000L, s.leftAgorot)
        assertEquals(0L, s.spentAgorot)
        assertEquals(0.0, s.spentPercent, 0.0)
        assertEquals(0f, s.progress)
        assertFalse(s.isWarning)
        assertFalse(s.isOverBudget)
        assertEquals(PerDay(100_000, 6), s.perDay)
    }

    @Test
    fun designExample_beforeTrip() {
        // ₪6,000 budget, ₪3,450 spent → ₪2,550 left, 57.5%, about ₪425 per day for 6 days.
        val s = checkNotNull(summary(6_000, listOf(expense(3_000), expense(450)), before))
        assertEquals(600_000L, s.totalAgorot)
        assertEquals(345_000L, s.spentAgorot)
        assertEquals(255_000L, s.leftAgorot)
        assertEquals(57.5, s.spentPercent, 1e-9)
        assertEquals(0.575f, s.progress, 1e-6f)
        assertFalse(s.isWarning)
        assertEquals(PerDay(42_500, 6), s.perDay)
    }

    @Test
    fun perDay_isRoundedDownToWholeShekels() {
        // ₪1,000 over 6 days = ₪166.66… → ₪166.
        assertEquals(PerDay(16_600, 6), summary(1_000, emptyList(), before)?.perDay)
    }

    @Test
    fun perDay_belowOneShekel_keepsAgorot() {
        // ₪3 left over 6 days = 50 agorot each.
        assertEquals(PerDay(50, 6), summary(6_000, listOf(expense(5_997)), before)?.perDay)
    }

    @Test
    fun warning_onlyAboveEightyPercent() {
        assertFalse(checkNotNull(summary(1_000, listOf(expense(800)), before)).isWarning)
        assertTrue(checkNotNull(summary(1_000, listOf(expense(800, agorot = 1)), before)).isWarning)
    }

    @Test
    fun exactlyOnBudget_isNotOver_andHasNoPerDay() {
        val s = checkNotNull(summary(1_000, listOf(expense(1_000)), before))
        assertEquals(0L, s.leftAgorot)
        assertFalse(s.isOverBudget)
        assertEquals(1f, s.progress)
        assertNull(s.perDay)
    }

    @Test
    fun overBudget_negativeLeft_fullBar_noPerDay() {
        val s = checkNotNull(summary(1_000, listOf(expense(1_200)), before))
        assertTrue(s.isOverBudget)
        assertTrue(s.isWarning)
        assertEquals(-20_000L, s.leftAgorot)
        assertEquals(120.0, s.spentPercent, 1e-9)
        assertEquals(1f, s.progress)
        assertNull(s.perDay)
    }

    @Test
    fun duringTrip_perDayCoversDaysLeftIncludingToday() {
        // 14 Nov: 14, 15, 16, 17 → 4 days.
        val s = summary(6_000, listOf(expense(2_000)), LocalDate.of(2026, 11, 14))
        assertEquals(PerDay(100_000, 4), s?.perDay)
        assertEquals(PerDay(400_000, 1), summary(6_000, listOf(expense(2_000)), end)?.perDay)
        assertEquals(PerDay(66_600, 6), summary(6_000, listOf(expense(2_000)), start)?.perDay)
    }

    @Test
    fun pastTrip_hidesPerDay_butKeepsTotals() {
        val s = checkNotNull(summary(6_000, listOf(expense(1_000)), LocalDate.of(2026, 11, 18)))
        assertNull(s.perDay)
        assertEquals(500_000L, s.leftAgorot)
    }

    @Test
    fun oneDayTrip_beforeAndOnTheDay() {
        val day = LocalDate.of(2026, 12, 1)
        assertEquals(PerDay(50_000, 1), summary(500, emptyList(), before, day, day)?.perDay)
        assertEquals(PerDay(30_000, 1), summary(500, listOf(expense(200)), day, day, day)?.perDay)
        assertNull(summary(500, emptyList(), day.plusDays(1), day, day)?.perDay)
    }

    @Test
    fun remainingBudgetDays_rules() {
        assertEquals(6, remainingBudgetDays(start, end, before))
        assertEquals(6, remainingBudgetDays(start, end, start))
        assertEquals(1, remainingBudgetDays(start, end, end))
        assertNull(remainingBudgetDays(start, end, end.plusDays(1)))
        assertEquals(1, remainingBudgetDays(start, start, start))
    }

    @Test
    fun categoryTotals_largestFirst_onlyCategoriesWithSpending() {
        val totals = categoryTotals(
            listOf(expense(1_800, FLIGHTS), expense(700, HOTELS), expense(500, HOTELS), expense(250, ATTRACTIONS)),
        )
        assertEquals(listOf(FLIGHTS, HOTELS, ATTRACTIONS), totals.map { it.category })
        assertEquals(listOf(180_000L, 120_000L, 25_000L), totals.map { it.amountAgorot })
        assertEquals(100.0, totals.sumOf { it.percent }, 1e-9)
        assertEquals(1_800 * 100.0 / 3_250, totals.first().percent, 1e-9)
    }

    @Test
    fun categoryTotals_tiesKeepCategoryOrder_andEmptyGivesNothing() {
        assertEquals(listOf(HOTELS, FOOD), categoryTotals(listOf(expense(10, FOOD), expense(10, HOTELS))).map { it.category })
        assertTrue(categoryTotals(emptyList()).isEmpty())
    }

    @Test
    fun categoryTotals_ofMyShare_listZeroCategoriesLastWhenAsked() {
        val food = expense(100, FOOD)
        val hotel = expense(500, HOTELS)
        // Only the hotel isn't mine (amountOf = 0).
        val mine: (Expense) -> Long = { if (it.id == hotel.id) 0L else it.amountAgorot }
        assertEquals(listOf(FOOD), categoryTotals(listOf(food, hotel), mine).map { it.category })
        val withZero = categoryTotals(listOf(food, hotel), mine, includeZeroCategories = true)
        assertEquals(listOf(FOOD to 10_000L, HOTELS to 0L), withZero.map { it.category to it.amountAgorot })
        assertEquals(0.0, withZero[1].percent, 0.0)
        // Nothing is mine: only ₪0 rows.
        assertEquals(
            listOf(HOTELS to 0L),
            categoryTotals(listOf(hotel), { 0L }, includeZeroCategories = true).map { it.category to it.amountAgorot },
        )
    }
}
