package com.example.planyourtrip.ui.common.split

import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.MergeBlock
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerMergeChanges
import com.example.planyourtrip.data.trip.TravelerMergeInput
import com.example.planyourtrip.data.trip.TravelerMergePlan
import com.example.planyourtrip.data.trip.TripMember
import com.example.planyourtrip.data.user.firstName

/**
 * A name-only traveler and a member of the trip are probably the same person: the traveler's name is the member's
 * display name or its first word ("Noa" and "Noa Levi"), ignoring case and spaces around it.
 */
fun isSameName(travelerName: String, memberName: String): Boolean {
    val traveler = travelerName.trim()
    val member = memberName.trim()
    if (traveler.isEmpty() || member.isEmpty()) return false
    return traveler.equals(member, ignoreCase = true) || traveler.equals(firstName(member), ignoreCase = true)
}

/** The trip's current members (in [memberIds], with a member document that has a name) whose name matches [name]. */
fun membersNamed(name: String, memberIds: List<String>, members: List<TripMember>): List<TripMember> =
    members.filter { it.uid in memberIds && !it.left && isSameName(name, it.name) }

/**
 * "Noa joined this trip. Merge with the traveler 'Noa'?": a name-only [traveler] that matches exactly one current
 * member ([memberKey], [memberName]; [isYou] = the signed-in member). [blocked] = why merging would change the
 * numbers (then it isn't offered), or null.
 */
data class MergeSuggestion(
    val traveler: Traveler,
    val memberKey: String,
    val memberName: String,
    val isYou: Boolean,
    val blocked: MergeBlock?,
)

/** Every merge to suggest, in the travelers' list order. A traveler matching two members is left alone. */
fun mergeSuggestions(
    me: String,
    memberIds: List<String>,
    members: List<TripMember>,
    travelers: List<Traveler>,
    expenses: List<Expense>,
    settlements: List<Settlement>,
): List<MergeSuggestion> = travelers.mapNotNull { traveler ->
    val member = membersNamed(traveler.name, memberIds, members).singleOrNull() ?: return@mapNotNull null
    val plan = planTravelerMerge(
        TravelerMergeInput(traveler.uuid, member.uid, me, memberIds, travelers, expenses, settlements),
    )
    MergeSuggestion(traveler, member.uid, member.name, member.uid == me, (plan as? TravelerMergePlan.Blocked)?.reason)
}

/**
 * What merging the traveler into the member changes. The traveler's key becomes the member's in every payer, split
 * and settlement; a settlement between the two becomes a payment to oneself and is deleted. Offered only when it
 * keeps every balance exactly: each other person's balance, and the member's = their own + the traveler's before.
 */
fun planTravelerMerge(input: TravelerMergeInput): TravelerMergePlan {
    val traveler = input.travelerKey
    val member = input.memberKey
    fun key(k: String) = if (k == traveler) member else k

    val used = input.expenses.filter { it.paidBy == traveler || traveler in it.splitBetween }
    if (used.any { it.splitBetween.ifEmpty { listOf(it.paidBy) }.let { split -> traveler in split && member in split } }) {
        return TravelerMergePlan.Blocked(MergeBlock.SHARED_TOGETHER)
    }
    val movedExpenses = used.map { it.copy(paidBy = key(it.paidBy), splitBetween = it.splitBetween.map(::key)) }
    val touched = input.settlements.filter { it.fromKey == traveler || it.toKey == traveler }
    val (toSelf, moved) = touched.map { it.copy(fromKey = key(it.fromKey), toKey = key(it.toKey)) }
        .partition { it.fromKey == it.toKey }

    val orderBefore = travelerOrder(input.me, input.memberIds, input.travelers)
    val orderAfter = travelerOrder(input.me, input.memberIds, input.travelers.filter { it.uuid != traveler })
    val before = balances(input.expenses, input.settlements, orderBefore)
    val expensesAfter = input.expenses.map { e -> movedExpenses.firstOrNull { it.id == e.id } ?: e }
    val settlementsAfter = input.settlements.filter { s -> toSelf.none { it.id == s.id } }
        .map { s -> moved.firstOrNull { it.id == s.id } ?: s }
    val after = balances(expensesAfter, settlementsAfter, orderAfter)

    val expected = (before - traveler).toMutableMap()
    expected[member] = (before[member] ?: 0L) + (before[traveler] ?: 0L)
    val keys = expected.keys + after.keys
    if (keys.any { (expected[it] ?: 0L) != (after[it] ?: 0L) }) {
        return TravelerMergePlan.Blocked(MergeBlock.BALANCES_WOULD_CHANGE)
    }
    return TravelerMergePlan.Ready(TravelerMergeChanges(movedExpenses, moved, toSelf.map { it.id }))
}
