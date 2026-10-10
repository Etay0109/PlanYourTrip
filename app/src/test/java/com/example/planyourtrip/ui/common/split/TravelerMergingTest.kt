package com.example.planyourtrip.ui.common.split

import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.MergeBlock
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.TravelerMergeChanges
import com.example.planyourtrip.data.trip.TravelerMergeInput
import com.example.planyourtrip.data.trip.TravelerMergePlan
import com.example.planyourtrip.data.trip.TripMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * Merging a name-only traveler into the member they turned out to be. The first tests use the exact rows of the two
 * shared trips on the phone on 2026-10-10 (account A "Etay Zahavi", account B "Yarden Tal Ronen").
 */
class TravelerMergingTest {

    private val a = "uidAmitFake00000000000000001"
    private val b = "UidYardenFake000000000000002"
    private val day = LocalDate.of(2026, 10, 8)

    private fun expense(id: Long, amount: Long, paidBy: String, split: List<String>) =
        Expense(id, 1, "e$id", amount, ExpenseCategory.OTHER, day, paidBy = paidBy, splitBetween = split)

    private fun settlement(id: Long, from: String, to: String, amount: Long) = Settlement(id, 1, "s$id", from, to, amount, day)

    private fun member(uid: String, name: String) = TripMember(uid, name, joinedAt = 1, left = false)

    // ---------- London: A owns it, B joined; "Yarden" was added by hand before ----------

    private val yarden = Traveler(1, 23, "20ef1952-b53d-42bc-b216-8df7f77ce7c3", "Yarden", TravelerColor.ROSE)
    private val london = TravelerMergeInput(
        travelerKey = yarden.uuid,
        memberKey = b,
        me = a,
        memberIds = listOf(a, b),
        travelers = listOf(yarden),
        expenses = listOf(
            expense(1, 30_000, paidBy = a, split = listOf(a, yarden.uuid)),
            expense(2, 25_000, paidBy = yarden.uuid, split = listOf(a, yarden.uuid)),
        ),
        settlements = listOf(
            settlement(1, yarden.uuid, a, 2_500),
            settlement(2, a, yarden.uuid, 60_765),
            settlement(3, a, yarden.uuid, 1_013),
            settlement(4, yarden.uuid, a, 61_778),
        ),
    )
    private val londonMembers = listOf(member(a, "Etay Zahavi"), member(b, "Yarden Tal Ronen"))

    // ---------- Crete: B owns it, A joined; "Etay" (A) was added by B before ----------

    private val etay = Traveler(2, 24, "a297cdf8-d936-409f-9e66-113c4057fcfd", "Etay", TravelerColor.ROSE)
    private val crete = TravelerMergeInput(
        travelerKey = etay.uuid,
        memberKey = a,
        me = a,
        memberIds = listOf(b, a),
        travelers = listOf(etay),
        expenses = listOf(
            expense(3, 171_450, paidBy = b, split = listOf(b, etay.uuid)),
            // Odd: the extra agora goes to the first in list order, before and after the merge.
            expense(4, 1_715, paidBy = etay.uuid, split = listOf(b, etay.uuid)),
        ),
        settlements = listOf(settlement(5, etay.uuid, b, 85_725)),
    )
    private val creteMembers = listOf(member(b, "Yarden Tal Ronen"), member(a, "Etay Zahavi"))

    @Test
    fun theSameNames_areTheMemberNameOrItsFirstWord_ignoringCaseAndSpaces() {
        assertTrue(isSameName("Yarden", "Yarden Tal Ronen"))
        assertTrue(isSameName(" yarden tal ronen ", "Yarden Tal Ronen"))
        assertTrue(isSameName("ETAY", "Etay Zahavi"))
        assertFalse(isSameName("Tal", "Yarden Tal Ronen"))
        assertFalse(isSameName("Yarde", "Yarden Tal Ronen"))
        assertFalse(isSameName("", "Yarden"))
    }

    @Test
    fun london_suggestsMergingYardenIntoTheMember() {
        val suggestions = mergeSuggestions(a, london.memberIds, londonMembers, london.travelers, london.expenses, london.settlements)

        assertEquals(listOf(MergeSuggestion(yarden, b, "Yarden Tal Ronen", isYou = false, blocked = null)), suggestions)
    }

    @Test
    fun crete_suggestsMergingEtayIntoMe() {
        val suggestions = mergeSuggestions(a, crete.memberIds, creteMembers, crete.travelers, crete.expenses, crete.settlements)

        assertEquals(listOf(MergeSuggestion(etay, a, "Etay Zahavi", isYou = true, blocked = null)), suggestions)
    }

    @Test
    fun london_merge_movesEverything_andKeepsEveryBalance() {
        val changes = ready(london)

        assertEquals(listOf(b, b), changes.expenses.map { it.splitBetween.last() })
        assertEquals(b, changes.expenses.single { it.id == 2L }.paidBy)
        assertEquals(4, changes.settlements.size)
        assertTrue(changes.deletedSettlementIds.isEmpty())
        assertBalancesKept(london, changes)
    }

    @Test
    fun crete_merge_keepsEveryBalance_includingTheOddAgora() {
        val changes = ready(crete)

        assertEquals(listOf(b, a), changes.expenses.single { it.id == 4L }.splitBetween)
        assertEquals(Settlement(5, 1, "s5", a, b, 85_725, day), changes.settlements.single())
        assertBalancesKept(crete, changes)
    }

    @Test
    fun aSettlementBetweenTheTwo_isDeleted_itIsMoneyToOneself() {
        val input = london.copy(settlements = london.settlements + settlement(9, b, yarden.uuid, 500))

        val changes = ready(input)

        assertEquals(listOf(9L), changes.deletedSettlementIds)
        assertBalancesKept(input, changes)
    }

    @Test
    fun anExpenseSplitBetweenBoth_blocksTheMerge() {
        val input = london.copy(expenses = london.expenses + expense(5, 9_000, paidBy = a, split = listOf(a, b, yarden.uuid)))

        assertEquals(TravelerMergePlan.Blocked(MergeBlock.SHARED_TOGETHER), planTravelerMerge(input))
    }

    @Test
    fun anExtraAgoraThatWouldMoveToSomeoneElse_blocksTheMerge() {
        // Members B (owner), A (me); "Bo" is B. Split between me and Bo, odd: I get the extra agora (Bo is after me).
        // Merged, B comes first in the list order and would get it: my balance would change by one agora.
        val bo = Traveler(3, 1, "bo", "Bo", TravelerColor.SKY)
        val input = TravelerMergeInput(
            "bo", b, a, listOf(b, a), listOf(bo), listOf(expense(6, 101, paidBy = a, split = listOf(a, "bo"))), emptyList(),
        )

        assertEquals(TravelerMergePlan.Blocked(MergeBlock.BALANCES_WOULD_CHANGE), planTravelerMerge(input))
    }

    @Test
    fun aNameMatchingTwoMembers_isNeverSuggested() {
        val noa = Traveler(4, 1, "noa", "Noa", TravelerColor.SKY)
        val members = listOf(member(a, "Noa Levi"), member(b, "Noa Cohen"))

        assertTrue(mergeSuggestions(a, listOf(a, b), members, listOf(noa), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun aMemberWhoLeft_isNotMatched() {
        val members = listOf(member(a, "Etay Zahavi"), member(b, "Yarden Tal Ronen").copy(left = true))

        assertTrue(mergeSuggestions(a, listOf(a), members, listOf(yarden), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun theTravelerIsLabeledNameOnly_untilMerged_andEveryoneIsListedOnce() {
        val before = people(a, london.travelers, london.memberIds, londonMembers)
        assertEquals(listOf(a, b, yarden.uuid), before.map { it.key })
        assertTrue((before.last() as Person.Named).nameOnly)

        val after = people(a, emptyList(), london.memberIds, londonMembers)
        assertEquals(listOf(a, b), after.map { it.key })
    }

    @Test
    fun aTravelerWithAnotherName_isNotLabeled() {
        val dana = Traveler(5, 1, "dana", "Dana", TravelerColor.SKY)

        val person = people(a, listOf(dana), london.memberIds, londonMembers).last() as Person.Named

        assertFalse(person.nameOnly)
        assertNull(mergeSuggestions(a, london.memberIds, londonMembers, listOf(dana), emptyList(), emptyList()).firstOrNull())
    }

    /** Random trips: every merge that is offered keeps every balance, recomputed independently of the planner. */
    @Test
    fun property_everyOfferedMerge_keepsEveryBalance() {
        val random = Random(42)
        var offered = 0
        repeat(500) {
            val memberIds = listOf("m0", "m1", "m2").take(random.nextInt(2, 4))
            val travelers = (0 until random.nextInt(1, 4)).map { Traveler(it.toLong(), 1, "t$it", "T$it", TravelerColor.SKY) }
            val keys = memberIds + travelers.map { it.uuid }
            val expenses = (1..random.nextInt(1, 6)).map { id ->
                val split = keys.shuffled(random).take(random.nextInt(1, keys.size + 1))
                expense(id.toLong(), random.nextLong(1, 50_000), keys.random(random), split)
            }
            val settlements = (1..random.nextInt(0, 3)).mapNotNull { id ->
                val (from, to) = keys.shuffled(random).take(2)
                settlement(id.toLong(), from, to, random.nextLong(1, 10_000))
            }
            val input = TravelerMergeInput(
                travelers.random(random).uuid, memberIds.random(random), memberIds.random(random), memberIds,
                travelers, expenses, settlements,
            )
            val plan = planTravelerMerge(input)
            if (plan is TravelerMergePlan.Ready) {
                offered++
                assertBalancesKept(input, plan.changes)
            }
        }
        assertTrue("only $offered of 500 merges offered", offered > 100)
    }

    private fun ready(input: TravelerMergeInput): TravelerMergeChanges =
        (planTravelerMerge(input) as TravelerMergePlan.Ready).changes

    /** Applies [changes] and checks: everyone else's balance is unchanged, the member's = theirs + the traveler's. */
    private fun assertBalancesKept(input: TravelerMergeInput, changes: TravelerMergeChanges) {
        val before = balances(input.expenses, input.settlements, travelerOrder(input.me, input.memberIds, input.travelers))
        val expensesAfter = input.expenses.map { e -> changes.expenses.firstOrNull { it.id == e.id } ?: e }
        val settlementsAfter = input.settlements.filter { it.id !in changes.deletedSettlementIds }
            .map { s -> changes.settlements.firstOrNull { it.id == s.id } ?: s }
        val remaining = input.travelers.filter { it.uuid != input.travelerKey }
        val after = balances(expensesAfter, settlementsAfter, travelerOrder(input.me, input.memberIds, remaining))

        assertFalse(expensesAfter.any { input.travelerKey == it.paidBy || input.travelerKey in it.splitBetween })
        assertFalse(settlementsAfter.any { input.travelerKey == it.fromKey || input.travelerKey == it.toKey })
        assertFalse(settlementsAfter.any { it.fromKey == it.toKey })
        for (key in before.keys - input.travelerKey - input.memberKey) assertEquals(key, before[key] ?: 0L, after[key] ?: 0L)
        assertEquals(
            (before[input.memberKey] ?: 0L) + (before[input.travelerKey] ?: 0L),
            after[input.memberKey] ?: 0L,
        )
        assertEquals(0L, after.values.sum())
    }
}
