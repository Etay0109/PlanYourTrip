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
 * Merging a name-only traveler into the member they turned out to be. The first tests use two shared trips shaped like
 * a real case (fictional people, ids and amounts): account A "Amit Shamir", account B "Yarden Tal Ronen".
 */
class TravelerMergingTest {

    private val a = "uidAmitFake00000000000000001"
    private val b = "UidYardenFake000000000000002"
    private val day = LocalDate.of(2026, 10, 8)

    private fun expense(id: Long, amount: Long, paidBy: String, split: List<String>) =
        Expense(id, 1, "e$id", amount, ExpenseCategory.OTHER, day, paidBy = paidBy, splitBetween = split)

    private fun settlement(id: Long, from: String, to: String, amount: Long) = Settlement(id, 1, "s$id", from, to, amount, day)

    private fun member(uid: String, name: String) = TripMember(uid, name, joinedAt = 1, left = false)

    // ---------- Lisbon: A owns it, B joined; "Yarden" was added by hand before ----------

    private val yarden = Traveler(1, 23, "11111111-1111-4111-8111-111111111111", "Yarden", TravelerColor.ROSE)
    private val lisbon = TravelerMergeInput(
        travelerKey = yarden.uuid,
        memberKey = b,
        me = a,
        memberIds = listOf(a, b),
        travelers = listOf(yarden),
        expenses = listOf(
            expense(1, 42_000, paidBy = a, split = listOf(a, yarden.uuid)),
            expense(2, 18_600, paidBy = yarden.uuid, split = listOf(a, yarden.uuid)),
        ),
        settlements = listOf(
            settlement(1, yarden.uuid, a, 3_100),
            settlement(2, a, yarden.uuid, 47_320),
            settlement(3, a, yarden.uuid, 1_207),
            settlement(4, yarden.uuid, a, 48_527),
        ),
    )
    private val lisbonMembers = listOf(member(a, "Amit Shamir"), member(b, "Yarden Tal Ronen"))

    // ---------- Rhodes: B owns it, A joined; "Amit" (A) was added by B before ----------

    private val amit = Traveler(2, 24, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "Amit", TravelerColor.ROSE)
    private val rhodes = TravelerMergeInput(
        travelerKey = amit.uuid,
        memberKey = a,
        me = a,
        memberIds = listOf(b, a),
        travelers = listOf(amit),
        expenses = listOf(
            expense(3, 126_840, paidBy = b, split = listOf(b, amit.uuid)),
            // Odd: the extra agora goes to the first in list order, before and after the merge.
            expense(4, 1_333, paidBy = amit.uuid, split = listOf(b, amit.uuid)),
        ),
        settlements = listOf(settlement(5, amit.uuid, b, 63_420)),
    )
    private val rhodesMembers = listOf(member(b, "Yarden Tal Ronen"), member(a, "Amit Shamir"))

    @Test
    fun theSameNames_areTheMemberNameOrItsFirstWord_ignoringCaseAndSpaces() {
        assertTrue(isSameName("Yarden", "Yarden Tal Ronen"))
        assertTrue(isSameName(" yarden tal ronen ", "Yarden Tal Ronen"))
        assertTrue(isSameName("AMIT", "Amit Shamir"))
        assertFalse(isSameName("Tal", "Yarden Tal Ronen"))
        assertFalse(isSameName("Yarde", "Yarden Tal Ronen"))
        assertFalse(isSameName("", "Yarden"))
    }

    @Test
    fun lisbon_suggestsMergingYardenIntoTheMember() {
        val suggestions = mergeSuggestions(a, lisbon.memberIds, lisbonMembers, lisbon.travelers, lisbon.expenses, lisbon.settlements)

        assertEquals(listOf(MergeSuggestion(yarden, b, "Yarden Tal Ronen", isYou = false, blocked = null)), suggestions)
    }

    @Test
    fun rhodes_suggestsMergingAmitIntoMe() {
        val suggestions = mergeSuggestions(a, rhodes.memberIds, rhodesMembers, rhodes.travelers, rhodes.expenses, rhodes.settlements)

        assertEquals(listOf(MergeSuggestion(amit, a, "Amit Shamir", isYou = true, blocked = null)), suggestions)
    }

    @Test
    fun lisbon_merge_movesEverything_andKeepsEveryBalance() {
        val changes = ready(lisbon)

        assertEquals(listOf(b, b), changes.expenses.map { it.splitBetween.last() })
        assertEquals(b, changes.expenses.single { it.id == 2L }.paidBy)
        assertEquals(4, changes.settlements.size)
        assertTrue(changes.deletedSettlementIds.isEmpty())
        assertBalancesKept(lisbon, changes)
    }

    @Test
    fun rhodes_merge_keepsEveryBalance_includingTheOddAgora() {
        val changes = ready(rhodes)

        assertEquals(listOf(b, a), changes.expenses.single { it.id == 4L }.splitBetween)
        assertEquals(Settlement(5, 1, "s5", a, b, 63_420, day), changes.settlements.single())
        assertBalancesKept(rhodes, changes)
    }

    @Test
    fun aSettlementBetweenTheTwo_isDeleted_itIsMoneyToOneself() {
        val input = lisbon.copy(settlements = lisbon.settlements + settlement(9, b, yarden.uuid, 500))

        val changes = ready(input)

        assertEquals(listOf(9L), changes.deletedSettlementIds)
        assertBalancesKept(input, changes)
    }

    @Test
    fun anExpenseSplitBetweenBoth_blocksTheMerge() {
        val input = lisbon.copy(expenses = lisbon.expenses + expense(5, 9_000, paidBy = a, split = listOf(a, b, yarden.uuid)))

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
        val members = listOf(member(a, "Amit Shamir"), member(b, "Yarden Tal Ronen").copy(left = true))

        assertTrue(mergeSuggestions(a, listOf(a), members, listOf(yarden), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun theTravelerIsLabeledNameOnly_untilMerged_andEveryoneIsListedOnce() {
        val before = people(a, lisbon.travelers, lisbon.memberIds, lisbonMembers)
        assertEquals(listOf(a, b, yarden.uuid), before.map { it.key })
        assertTrue((before.last() as Person.Named).nameOnly)

        val after = people(a, emptyList(), lisbon.memberIds, lisbonMembers)
        assertEquals(listOf(a, b), after.map { it.key })
    }

    @Test
    fun aTravelerWithAnotherName_isNotLabeled() {
        val dana = Traveler(5, 1, "dana", "Dana", TravelerColor.SKY)

        val person = people(a, listOf(dana), lisbon.memberIds, lisbonMembers).last() as Person.Named

        assertFalse(person.nameOnly)
        assertNull(mergeSuggestions(a, lisbon.memberIds, lisbonMembers, listOf(dana), emptyList(), emptyList()).firstOrNull())
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
