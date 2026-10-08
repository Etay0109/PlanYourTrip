package com.example.planyourtrip.data.trip

import java.time.LocalDate

/**
 * The key of the signed-in user ("You") in an expense's payer and split and in settlements. "You" is never stored as
 * a traveler row: every trip has it, it can't be renamed or removed, and it is always first in the list order.
 * Other travelers are referenced by their uuid.
 */
const val ME = "me"

/**
 * Someone the user travels with: just a name (not an account, never uses the app). [uuid] is the key used by
 * expenses and settlements. Listed in the order they were added, after "You".
 */
data class Traveler(
    val id: Long,
    val tripId: Long,
    val uuid: String,
    val name: String,
    val color: TravelerColor,
)

/**
 * A payment between two travelers to settle a debt: [fromKey] paid [toKey] [amountAgorot] on [date]. Never changes
 * the budget, only the balances. Keys are [ME] or a traveler uuid.
 */
data class Settlement(
    val id: Long,
    val tripId: Long,
    val uuid: String,
    val fromKey: String,
    val toKey: String,
    val amountAgorot: Long,
    val date: LocalDate,
)

/** Result of removing a traveler. */
enum class RemoveTravelerResult {
    REMOVED,

    /** The traveler paid for or shares an expense, or appears in a settlement; nothing was removed. */
    IN_USE,

    /** Not one of the owner's travelers. */
    NOT_FOUND,
}
