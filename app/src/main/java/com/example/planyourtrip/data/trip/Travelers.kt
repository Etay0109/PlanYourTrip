package com.example.planyourtrip.data.trip

import java.time.LocalDate

/**
 * Traveler keys (an expense's payer and split, a settlement's two sides) are a trip member's **account uid** or a
 * name-only traveler's uuid. Members are never stored as traveler rows: the signed-in member is shown as "You".
 *
 * Before shared trips, the account owner's key was the fixed text "me". The v14 Room migration and the cloud
 * migration (`data/sync/LegacyMigration.kt`) replace it with the owner's uid; only legacy data still holds it.
 * Also the SQL default of `expenses.paid_by` / `split_between` (SQLite can't change a column default).
 */
const val LEGACY_ME_KEY = "me"

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
 * the budget, only the balances. Keys are member uids or traveler uuids.
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
