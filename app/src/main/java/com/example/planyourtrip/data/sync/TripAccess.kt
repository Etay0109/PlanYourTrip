package com.example.planyourtrip.data.sync

/** What a direct read of a trip document says ([CloudStore.tripAccess]). */
enum class TripAccess {
    /** Readable and not deleted. */
    LIVE,

    /** Readable, but deleted by its owner (a tombstone). */
    DELETED,

    /** No such document. */
    MISSING,

    /** The read was denied: this account is not one of the trip's members (it left or was removed). */
    NO_ACCESS,
}

/**
 * Which local trips a full sync must read directly ([CloudStore.tripAccess]) after the "trips where I am a member"
 * list query succeeded: trips the list didn't return, and trips it returned as deleted that [me] doesn't own.
 *
 * [localOwners] = each local trip's uuid → its cloud owner; [listed] = each trip the list returned → deleted.
 */
fun tripsToCheck(me: String, localOwners: Map<String, String>, listed: Map<String, Boolean>): Set<String> =
    localOwners.filter { (uuid, owner) ->
        val deleted = listed[uuid]
        deleted == null || (deleted && !isOwnTrip(owner, me))
    }.keys

/** The result of [decideAccess]. */
data class AccessDecision(
    /** Trips this device lost: removed here with their pending changes and documents. */
    val lost: Set<String> = emptySet(),
    /** My own trips whose read was denied: a sync error (nothing is removed; the sync retries later). */
    val ownTripsDenied: Set<String> = emptySet(),
)

/**
 * Decides which local trips [me] lost, after a full download. Pure, so the rules are easy to test:
 * - A trip I own ([localOwners] says so, or no owner is known) is **never** lost; a denied read of it is a sync error.
 * - A trip I don't own is lost only when **both** hold: the list query (which succeeded, or this isn't called) didn't
 *   return it alive ([listed]: missing or deleted), **and** its direct read ([access]) was denied, found nothing or
 *   found it deleted. A trip without a direct read result is kept.
 */
fun decideAccess(
    me: String,
    localOwners: Map<String, String>,
    listed: Map<String, Boolean>,
    access: Map<String, TripAccess>,
): AccessDecision {
    val lost = mutableSetOf<String>()
    val ownDenied = mutableSetOf<String>()
    for ((uuid, owner) in localOwners) {
        val read = access[uuid] ?: continue
        if (isOwnTrip(owner, me)) {
            if (read == TripAccess.NO_ACCESS) ownDenied += uuid
            continue
        }
        val notListedAlive = listed[uuid] != false
        if (notListedAlive && read != TripAccess.LIVE) lost += uuid
    }
    return AccessDecision(lost, ownDenied)
}

/** A trip whose cloud owner is [me], or unknown (blank: treated as mine, so it is never removed by mistake). */
fun isOwnTrip(owner: String?, me: String): Boolean = owner.isNullOrBlank() || owner == me
