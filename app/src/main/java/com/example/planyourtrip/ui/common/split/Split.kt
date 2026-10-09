package com.example.planyourtrip.ui.common.split

import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.Traveler

// Pure expense-splitting math (no Android dependencies, unit-tested). All money is whole agorot. Travelers are
// referenced by key: a trip member's account uid (the signed-in one, `me`, is "You") or a name-only traveler's uuid.
// Shared by the Budget tab, the Overview budget card, the shared PDF and the assistant's trip context.

/** [fromKey] owes [toKey] [amountAgorot] ("Noa owes you ₪320"). */
data class Transfer(val fromKey: String, val toKey: String, val amountAgorot: Long)

/**
 * The list order of a trip's travelers, the same on every device (the extra agorot of an uneven split go in this
 * order): the members ([memberIds]: the owner, then in join order), then the name-only travelers in the order they
 * were added. For a trip with one member this is "You" first, then the travelers, as before shared trips. [me] is
 * always a member; were it missing (a trip without member data), it comes first.
 */
fun travelerOrder(me: String, memberIds: List<String>, travelers: List<Traveler>): List<String> =
    (if (me in memberIds) memberIds else listOf(me) + memberIds) + travelers.map { it.uuid }

/**
 * [keys] without duplicates, in list [order]. Keys that are not in [order] (a traveler removed on another device
 * while this one used them) come last, sorted by key, so every device computes the same result.
 */
fun inListOrder(keys: Collection<String>, order: List<String>): List<String> {
    val index = order.withIndex().associate { (i, key) -> key to i }
    return keys.distinct().sortedWith(compareBy<String>({ index[it] ?: Int.MAX_VALUE }, { it }))
}

/**
 * Equal shares of [amountAgorot] between [splitBetween], in list order. When the amount doesn't divide evenly, the
 * first travelers in list order get one agora more each, so the shares always add up exactly to the amount.
 * An empty split gives an empty map.
 */
fun shares(amountAgorot: Long, splitBetween: List<String>, order: List<String>): Map<String, Long> {
    val keys = inListOrder(splitBetween, order)
    if (keys.isEmpty()) return emptyMap()
    val base = amountAgorot / keys.size
    val extra = amountAgorot % keys.size
    return keys.withIndex().associate { (i, key) -> key to base + if (i < extra) 1 else 0 }
}

/** The expense's shares in list order (an empty split counts as the payer only). */
fun Expense.shares(order: List<String>): Map<String, Long> =
    shares(amountAgorot, splitBetween.ifEmpty { listOf(paidBy) }, order)

/** My ([me]'s) share of the expense: what the budget counts. 0 when I'm not part of the split. */
fun Expense.myShare(order: List<String>, me: String): Long = shares(order)[me] ?: 0L

/** My share of every expense together (the budget's "spent"). */
fun myTotal(expenses: List<Expense>, order: List<String>, me: String): Long =
    expenses.sumOf { it.myShare(order, me) }

/** Shared = involves someone besides the payer (not "not shared"). */
fun Expense.isShared(): Boolean = splitBetween.ifEmpty { listOf(paidBy) }.distinct() != listOf(paidBy)

/** "Based on N shared expenses". */
fun sharedExpenseCount(expenses: List<Expense>): Int = expenses.count { it.isShared() }

/**
 * Every traveler's balance: what they paid minus their shares, plus the settlements they paid, minus the ones they
 * received. Positive = others owe them. Contains every key of [order] (0 when nothing applies) and any unknown key an
 * expense or settlement uses; the values always add up to 0.
 */
fun balances(expenses: List<Expense>, settlements: List<Settlement>, order: List<String>): Map<String, Long> {
    val balance = LinkedHashMap<String, Long>()
    order.forEach { balance[it] = 0L }
    fun add(key: String, amount: Long) {
        balance[key] = (balance[key] ?: 0L) + amount
    }
    for (expense in expenses) {
        add(expense.paidBy, expense.amountAgorot)
        expense.shares(order).forEach { (key, share) -> add(key, -share) }
    }
    for (settlement in settlements) {
        add(settlement.fromKey, settlement.amountAgorot)
        add(settlement.toKey, -settlement.amountAgorot)
    }
    return inListOrder(balance.keys, order).associateWith { balance.getValue(it) }
}

/** Above this many people with a non-zero balance the exact search is skipped (2^n states). */
private const val MAX_EXACT_PEOPLE = 16

/**
 * The smallest number of transfers that settles [balances] (which add up to 0). The people with a non-zero balance
 * are split into as many groups that add up to 0 as possible (exact search over subsets); each group of k people
 * settles with k − 1 transfers, largest debtor paying largest creditor. Sorted: transfers that involve [me] first,
 * then the largest amount, then list order.
 */
fun simplifyTransfers(balances: Map<String, Long>, order: List<String>, me: String): List<Transfer> {
    val people = inListOrder(balances.filterValues { it != 0L }.keys, order)
    val amounts = people.map { balances.getValue(it) }
    val groups = if (people.size <= MAX_EXACT_PEOPLE) zeroSumGroups(amounts) else listOf(people.indices.toList())
    val transfers = groups.flatMap { group -> settleGroup(group.map { people[it] to amounts[it] }) }
    val index = order.withIndex().associate { (i, key) -> key to i }
    return transfers.sortedWith(
        compareBy<Transfer>(
            { if (it.fromKey == me || it.toKey == me) 0 else 1 },
            { -it.amountAgorot },
            { index[it.fromKey] ?: Int.MAX_VALUE },
            { index[it.toKey] ?: Int.MAX_VALUE },
            { it.fromKey },
            { it.toKey },
        )
    )
}

/**
 * Splits the indices of [amounts] (adding up to 0) into the largest number of groups that each add up to 0.
 * Dynamic programming over subsets: removing people one at a time, count how many of the remaining sets add up to 0;
 * the sets on the best chain mark the group boundaries.
 */
private fun zeroSumGroups(amounts: List<Long>): List<List<Int>> {
    val n = amounts.size
    if (n == 0) return emptyList()
    val full = (1 shl n) - 1
    val sum = LongArray(1 shl n)
    for (mask in 1..full) {
        val low = Integer.numberOfTrailingZeros(mask)
        sum[mask] = sum[mask and (mask - 1)] + amounts[low]
    }
    val best = IntArray(1 shl n)
    val removed = IntArray(1 shl n)
    for (mask in 1..full) {
        var bestValue = -1
        var bestIndex = -1
        for (i in 0 until n) {
            if (mask and (1 shl i) == 0) continue
            val value = best[mask xor (1 shl i)]
            if (value > bestValue) {
                bestValue = value
                bestIndex = i
            }
        }
        best[mask] = bestValue + if (sum[mask] == 0L) 1 else 0
        removed[mask] = bestIndex
    }
    // Walk the chain from everyone down to nobody; a group ends each time the remaining set adds up to 0.
    val groups = mutableListOf<List<Int>>()
    var current = mutableListOf<Int>()
    var mask = full
    while (mask != 0) {
        val i = removed[mask]
        current += i
        mask = mask xor (1 shl i)
        if (sum[mask] == 0L) {
            groups += current.sorted()
            current = mutableListOf()
        }
    }
    if (current.isNotEmpty()) groups += current.sorted()
    return groups
}

/** Settles one group (adding up to 0): the largest debtor pays the largest creditor until everyone is at 0. */
private fun settleGroup(members: List<Pair<String, Long>>): List<Transfer> {
    // Stable order for equal amounts: the order the members came in (list order).
    val debtors = members.filter { it.second < 0 }.map { it.first to -it.second }.toMutableList()
    val creditors = members.filter { it.second > 0 }.toMutableList()
    val transfers = mutableListOf<Transfer>()
    while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
        val d = debtors.indices.maxBy { debtors[it].second }
        val c = creditors.indices.maxBy { creditors[it].second }
        val (debtor, owes) = debtors[d]
        val (creditor, owed) = creditors[c]
        val amount = minOf(owes, owed)
        transfers += Transfer(debtor, creditor, amount)
        if (owes == amount) debtors.removeAt(d) else debtors[d] = debtor to owes - amount
        if (owed == amount) creditors.removeAt(c) else creditors[c] = creditor to owed - amount
    }
    return transfers
}

/** Whether [key] paid for or shares an expense, or appears in a settlement (such a traveler can't be removed). */
fun isTravelerUsed(key: String, expenses: List<Expense>, settlements: List<Settlement>): Boolean =
    expenses.any { it.paidBy == key || key in it.splitBetween } ||
        settlements.any { it.fromKey == key || it.toKey == key }
