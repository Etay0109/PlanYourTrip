package com.example.planyourtrip.ui.tripdetails.split

import com.example.planyourtrip.ME
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.common.split.Transfer
import com.example.planyourtrip.ui.common.split.balances
import com.example.planyourtrip.ui.common.split.simplifyTransfers
import com.example.planyourtrip.ui.common.split.travelerOrder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwesSummaryTest {
    private val day = LocalDate.of(2026, 10, 20)
    private val order = travelerOrder(
        ME,
        listOf(ME),
        listOf(
            Traveler(1, 1, "noa", "Noa", TravelerColor.ROSE),
            Traveler(2, 1, "amit", "Amit", TravelerColor.INDIGO),
        ),
    )
    private val everyone = listOf(ME, "noa", "amit")

    private var nextId = 1L
    private fun expense(agorot: Long, paidBy: String, split: List<String> = everyone) =
        Expense(nextId++, 1, "e", agorot, ExpenseCategory.FOOD, day, paidBy = paidBy, splitBetween = split)

    private fun settlement(from: String, to: String, agorot: Long) =
        Settlement(nextId++, 1, "s$nextId", from, to, agorot, day)

    private fun summary(expenses: List<Expense>, settlements: List<Settlement> = emptyList()): OwesSummary? =
        owesSummary(simplifyTransfers(balances(expenses, settlements, order), order, ME), ME)

    @Test
    fun othersOweMe_isOwedTheSumOfTheirDebts() {
        // ₪300 paid by me for three: Noa and Amit owe ₪100 each.
        assertEquals(OwesSummary.Owed(20_000), summary(listOf(expense(30_000, paidBy = ME))))
    }

    @Test
    fun iOweOthers_isOweTheSumOfMyDebts() {
        val expenses = listOf(expense(30_000, paidBy = "noa"), expense(15_000, paidBy = "amit"))
        // My share 100 + 50 = ₪150 owed, I paid nothing.
        assertEquals(OwesSummary.Owe(15_000), summary(expenses))
    }

    @Test
    fun iAmAtZeroWhileOthersStillOwe_isAllSquare() {
        // Noa paid ₪100 for Amit only; my own expense isn't shared.
        val expenses = listOf(
            expense(10_000, paidBy = "noa", split = listOf("amit")),
            expense(5_000, paidBy = ME, split = listOf(ME)),
        )
        assertEquals(OwesSummary.AllSquare, summary(expenses))
    }

    @Test
    fun everyoneSettled_hasNoSummary() {
        assertNull(summary(emptyList()))
        val expenses = listOf(expense(30_000, paidBy = ME))
        val settlements = listOf(settlement("noa", ME, 10_000), settlement("amit", ME, 10_000))
        assertNull(summary(expenses, settlements))
    }

    @Test
    fun partialSettlement_leavesTheRest() {
        val expenses = listOf(expense(30_000, paidBy = ME))
        assertEquals(OwesSummary.Owed(15_000), summary(expenses, listOf(settlement("noa", ME, 5_000))))
    }

    @Test
    fun unevenSplit_isExactToTheAgora() {
        // ₪100 / 3: my share is 33.34 (the extra agora is mine), so the others owe me 33.33 each = ₪66.66.
        val owed = summary(listOf(expense(10_000, paidBy = ME)))
        assertEquals(OwesSummary.Owed(6_666), owed)
        assertEquals("66.66", Money.formatAgorot((owed as OwesSummary.Owed).amountAgorot))
        // Noa paid ₪1.01 for three: my share 0.34.
        val owe = summary(listOf(expense(101, paidBy = "noa")))
        assertEquals(OwesSummary.Owe(34), owe)
        assertEquals("0.34", Money.formatAgorot((owe as OwesSummary.Owe).amountAgorot))
    }

    @Test
    fun owedAndOwingAtOnce_isTheNet() {
        // A group may hold me on both sides; only the net counts.
        val transfers = listOf(Transfer("noa", ME, 82_049), Transfer(ME, "amit", 2_000))
        assertEquals(OwesSummary.Owed(80_049), owesSummary(transfers, ME))
        assertEquals(OwesSummary.AllSquare, owesSummary(listOf(Transfer("noa", "amit", 500)), ME))
    }
}
