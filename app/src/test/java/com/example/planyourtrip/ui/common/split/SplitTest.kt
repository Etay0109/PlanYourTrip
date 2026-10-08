package com.example.planyourtrip.ui.common.split

import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ME
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.ui.common.categoryTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class SplitTest {
    private val day = LocalDate.of(2026, 10, 20)
    private val noa = Traveler(1, 1, "noa", "Noa", TravelerColor.ROSE)
    private val amit = Traveler(2, 1, "amit", "Amit", TravelerColor.INDIGO)
    private val travelers = listOf(noa, amit)
    private val order = travelerOrder(travelers)
    private val everyone = listOf(ME, "noa", "amit")

    private var nextId = 1L
    private fun expense(
        agorot: Long,
        paidBy: String = ME,
        split: List<String> = listOf(paidBy),
        category: ExpenseCategory = ExpenseCategory.FOOD,
    ) = Expense(nextId++, 1, "e", agorot, category, day, paidBy = paidBy, splitBetween = split)

    private fun settlement(from: String, to: String, agorot: Long) =
        Settlement(nextId++, 1, "s$nextId", from, to, agorot, day)

    private fun transfers(expenses: List<Expense>, settlements: List<Settlement> = emptyList()) =
        simplifyTransfers(balances(expenses, settlements, order), order)

    @Test
    fun order_isYouFirstThenTravelersAsAdded() {
        assertEquals(listOf(ME, "noa", "amit"), order)
        assertEquals(listOf(ME), travelerOrder(emptyList()))
    }

    @Test
    fun unevenSplit_givesTheExtraAgorotToTheFirstInListOrder() {
        // ₪100 / 3 = 33.33 r 1: "You" (first) gets the extra agora.
        assertEquals(mapOf(ME to 3_334L, "noa" to 3_333L, "amit" to 3_333L), shares(10_000, everyone, order))
        // ₪1.01 / 3 = 0.33 r 2: the first two get one more.
        assertEquals(mapOf(ME to 34L, "noa" to 34L, "amit" to 33L), shares(101, everyone, order))
        // Split order doesn't matter, list order does.
        assertEquals(mapOf("noa" to 51L, "amit" to 50L), shares(101, listOf("amit", "noa"), order))
    }

    @Test
    fun evenSplit_isExact() {
        assertEquals(mapOf(ME to 6_000L, "noa" to 6_000L, "amit" to 6_000L), shares(18_000, everyone, order))
    }

    @Test
    fun expenseIAmNotPartOf_countsZeroForMe() {
        val e = expense(9_000, paidBy = "noa", split = listOf("noa", "amit"))
        assertEquals(0L, e.myShare(order))
        assertEquals(0L, myTotal(listOf(e), order))
        assertTrue(e.isShared())
    }

    @Test
    fun notSharedExpense_isAllMineAndNotShared() {
        val e = expense(12_345)
        assertEquals(12_345L, e.myShare(order))
        assertFalse(e.isShared())
        assertTrue(transfers(listOf(e)).isEmpty())
    }

    @Test
    fun expenseIPaidForSomeoneElse_isSharedAndTheyOweMe() {
        val e = expense(5_000, paidBy = ME, split = listOf("noa"))
        assertEquals(0L, e.myShare(order))
        assertTrue(e.isShared())
        assertEquals(listOf(Transfer("noa", ME, 5_000)), transfers(listOf(e)))
    }

    @Test
    fun existingExpense_paidByMeNotShared_keepsTheFullAmount() {
        // What every expense becomes in the v13 migration.
        val old = Expense(1, 1, "Hotel", 250_000, ExpenseCategory.HOTELS, day)
        assertEquals(250_000L, old.myShare(order))
        assertEquals(250_000L, old.myShare(travelerOrder(emptyList())))
        assertEquals(0, sharedExpenseCount(listOf(old)))
    }

    @Test
    fun balances_arePaidMinusShares_andAddUpToZero() {
        val expenses = listOf(
            expense(18_000, paidBy = "noa", split = everyone),
            expense(21_600, paidBy = ME, split = everyone),
        )
        val b = balances(expenses, emptyList(), order)
        // Noa: 180 − 60 − 72 = 48; me: 216 − 60 − 72 = 84; Amit: −60 − 72 = −132.
        assertEquals(mapOf(ME to 8_400L, "noa" to 4_800L, "amit" to -13_200L), b)
        assertEquals(0L, b.values.sum())
    }

    @Test
    fun threeTravelersWithCrossingDebts_settleInTwoTransfers() {
        val expenses = listOf(
            expense(30_000, paidBy = ME, split = everyone), // each 100
            expense(15_000, paidBy = "noa", split = everyone), // each 50
            expense(6_000, paidBy = "amit", split = listOf(ME, "noa")), // each 30
        )
        // me: 300 − 100 − 50 − 30 = 120; Noa: 150 − 100 − 50 − 30 = −30; Amit: 60 − 100 − 50 = −90.
        val result = transfers(expenses)
        assertEquals(2, result.size)
        assertEquals(listOf(Transfer("amit", ME, 9_000), Transfer("noa", ME, 3_000)), result)
    }

    @Test
    fun transfersBetweenOthers_comeAfterMine() {
        val expenses = listOf(
            expense(10_000, paidBy = "noa", split = listOf("amit")), // Amit owes Noa 100
            expense(2_000, paidBy = ME, split = listOf("noa")), // Noa owes me 20
        )
        // Balances: me +20, Noa +80, Amit −100 → Amit pays both.
        val result = transfers(expenses)
        assertEquals(listOf(Transfer("amit", ME, 2_000), Transfer("amit", "noa", 8_000)), result)
    }

    @Test
    fun minimumTransfers_findsIndependentPairs() {
        // Four people: A owes B 10, C owes D 20 (as balances). Greedy by size alone could cross them (3 transfers).
        val keys = listOf(ME, "b", "c", "d")
        val balances = mapOf(ME to -1_000L, "b" to 1_000L, "c" to -2_000L, "d" to 2_000L)
        val result = simplifyTransfers(balances, keys)
        assertEquals(setOf(Transfer(ME, "b", 1_000), Transfer("c", "d", 2_000)), result.toSet())
    }

    @Test
    fun minimumTransfers_usesZeroSumGroups() {
        // +5 +3 −3 −5 +7 −7: three pairs (3 transfers) instead of a chain (5).
        val keys = listOf("a", "b", "c", "d", "e", "f")
        val balances = mapOf("a" to 500L, "b" to 300L, "c" to -300L, "d" to -500L, "e" to 700L, "f" to -700L)
        val result = simplifyTransfers(balances, keys)
        assertEquals(3, result.size)
        assertSettles(balances, result)
    }

    @Test
    fun partialSettlement_reducesTheDebt() {
        val expenses = listOf(expense(30_000, paidBy = "noa", split = listOf(ME, "noa")))
        assertEquals(listOf(Transfer(ME, "noa", 15_000)), transfers(expenses))
        val paid = listOf(settlement(ME, "noa", 5_000))
        assertEquals(listOf(Transfer(ME, "noa", 10_000)), transfers(expenses, paid))
    }

    @Test
    fun deletedSettlement_reopensTheDebt() {
        val expenses = listOf(expense(30_000, paidBy = "noa", split = listOf(ME, "noa")))
        val paid = settlement(ME, "noa", 15_000)
        assertTrue(transfers(expenses, listOf(paid)).isEmpty())
        // Deleting = the settlement is gone from the list.
        assertEquals(listOf(Transfer(ME, "noa", 15_000)), transfers(expenses, emptyList()))
    }

    @Test
    fun everythingSettled_givesNoTransfers() {
        val expenses = listOf(
            expense(9_000, paidBy = ME, split = everyone),
            expense(9_000, paidBy = "noa", split = everyone),
            expense(9_000, paidBy = "amit", split = everyone),
        )
        assertTrue(transfers(expenses).isEmpty())
        assertEquals(3, sharedExpenseCount(expenses))
    }

    @Test
    fun settlementsNeverChangeMyShare() {
        val expenses = listOf(expense(30_000, paidBy = "noa", split = listOf(ME, "noa")))
        val before = myTotal(expenses, order)
        transfers(expenses, listOf(settlement(ME, "noa", 15_000)))
        assertEquals(15_000L, before)
        assertEquals(before, myTotal(expenses, order))
    }

    @Test
    fun singleTraveler_hasNoTransfersAndFullAmounts() {
        val only = travelerOrder(emptyList())
        val expenses = listOf(expense(1_000), expense(2_550))
        assertEquals(3_550L, myTotal(expenses, only))
        assertTrue(simplifyTransfers(balances(expenses, emptyList(), only), only).isEmpty())
        assertEquals(mapOf(ME to 0L), balances(expenses, emptyList(), only))
    }

    @Test
    fun unknownTraveler_keepsTheNumbersExact() {
        // "gone" was removed on another device while this expense used it.
        val e = expense(10_000, paidBy = "gone", split = listOf(ME, "gone"))
        assertEquals(mapOf(ME to 5_000L, "gone" to 5_000L), e.shares(order))
        val b = balances(listOf(e), emptyList(), order)
        assertEquals(0L, b.values.sum())
        assertEquals(listOf(Transfer(ME, "gone", 5_000)), simplifyTransfers(b, order))
        assertEquals(Person.Removed("gone"), personOf("gone", travelers))
    }

    @Test
    fun emptySplit_countsAsThePayerOnly() {
        val e = expense(4_000, paidBy = "noa", split = emptyList())
        assertEquals(mapOf("noa" to 4_000L), e.shares(order))
        assertFalse(e.isShared())
    }

    @Test
    fun sharesCategoriesAndBalances_alwaysAddUpToTheExpenseAmounts() {
        val random = Random(42)
        val keys = listOf(ME, "noa", "amit", "dana", "yoni")
        val bigOrder = keys
        repeat(500) {
            val expenses = List(random.nextInt(1, 8)) {
                val split = keys.shuffled(random).take(random.nextInt(1, keys.size + 1))
                expense(
                    agorot = random.nextLong(1, 1_000_000),
                    paidBy = keys.random(random),
                    split = split,
                    category = ExpenseCategory.entries.random(random),
                )
            }
            for (e in expenses) {
                val s = e.shares(bigOrder)
                assertEquals(e.amountAgorot, s.values.sum())
                // Equal within one agora, the larger shares first in list order.
                assertTrue(s.values.max() - s.values.min() <= 1)
                assertEquals(s.values.toList(), s.values.sortedDescending())
            }
            val everyoneTotal = keys.sumOf { key -> expenses.sumOf { it.shares(bigOrder)[key] ?: 0L } }
            assertEquals(expenses.sumOf { it.amountAgorot }, everyoneTotal)
            val mine = myTotal(expenses, bigOrder)
            assertEquals(mine, categoryTotals(expenses, { it.myShare(bigOrder) }).sumOf { it.amountAgorot })
            val settlements = List(random.nextInt(0, 3)) {
                val (from, to) = keys.shuffled(random).take(2)
                settlement(from, to, random.nextLong(1, 100_000))
            }
            val b = balances(expenses, settlements, bigOrder)
            assertEquals(0L, b.values.sum())
            val t = simplifyTransfers(b, bigOrder)
            assertSettles(b, t)
            // Never more than people with a non-zero balance − 1.
            assertTrue(t.size <= maxOf(0, b.values.count { it != 0L } - 1))
        }
    }

    @Test
    fun travelerInUse_byPayerSplitOrSettlement() {
        val paid = expense(1_000, paidBy = "noa", split = listOf(ME))
        val shared = expense(1_000, paidBy = ME, split = listOf(ME, "amit"))
        assertTrue(isTravelerUsed("noa", listOf(paid), emptyList()))
        assertTrue(isTravelerUsed("amit", listOf(shared), emptyList()))
        assertTrue(isTravelerUsed("dana", emptyList(), listOf(settlement("dana", ME, 100))))
        assertFalse(isTravelerUsed("dana", listOf(paid, shared), emptyList()))
    }

    @Test
    fun travelerNames_mustBeNonBlankUniqueAndNotYou() {
        assertEquals(TravelerNameError.EMPTY, validateTravelerName("  ", travelers))
        assertEquals(TravelerNameError.DUPLICATE, validateTravelerName(" noa ", travelers))
        assertEquals(TravelerNameError.RESERVED, validateTravelerName("you", travelers))
        assertNull(validateTravelerName("Dana", travelers))
        // Renaming Noa to "NOA" is fine (her own name).
        assertNull(validateTravelerName("NOA", travelers, editingKey = "noa"))
        assertEquals(TravelerNameError.DUPLICATE, validateTravelerName("Amit", travelers, editingKey = "noa"))
    }

    @Test
    fun avatarLetter_isTheFirstLetterUppercase() {
        assertEquals("N", avatarLetter("noa"))
        assertEquals("נ", avatarLetter("נועה"))
        assertEquals("A", avatarLetter("  (Amit)"))
        assertEquals("?", avatarLetter("!!"))
    }

    /** Applying [transfers] brings every balance to 0. */
    private fun assertSettles(balances: Map<String, Long>, transfers: List<Transfer>) {
        val after = balances.toMutableMap()
        for (t in transfers) {
            assertTrue(t.amountAgorot > 0)
            after[t.fromKey] = after.getValue(t.fromKey) + t.amountAgorot
            after[t.toKey] = after.getValue(t.toKey) - t.amountAgorot
        }
        assertTrue(after.toString(), after.values.all { it == 0L })
    }
}
