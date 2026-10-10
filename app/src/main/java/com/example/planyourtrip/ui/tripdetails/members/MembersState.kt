package com.example.planyourtrip.ui.tripdetails.members

import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripMember
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.common.split.personOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One current member of the trip, as the "Traveling together" card and the members list show them. */
data class MemberEntry(
    val person: Person,
    val isOwner: Boolean,
    /** The day they joined ("Joined 9 Oct"), or null when unknown (before the next sync). */
    val joinedOn: LocalDate?,
) {
    val uid: String get() = person.key
}

/** The trip's current members for the signed-in member [me]. */
data class MembersState(
    val me: String,
    /** "You" first, then the others in join order (the owner first among them). */
    val members: List<MemberEntry>,
    /** Only the owner invites and removes members; everyone else can leave. */
    val isOwner: Boolean,
) {
    val count: Int get() = members.size

    /** A trip has at most 10 members (firestore.rules). */
    val isFull: Boolean get() = members.size >= MAX_MEMBERS

    companion object {
        const val MAX_MEMBERS = 10
    }
}

/**
 * The members of [trip] (its `memberIds`, or only [me] before the first sync) with the names of their member
 * documents ([members]); join days in [zone]. Pure.
 */
fun membersState(me: String, trip: Trip, members: List<TripMember>, zone: ZoneId): MembersState {
    val ids = trip.memberIds.ifEmpty { listOf(me) }
    val ownerId = trip.ownerId.ifBlank { ids.first() }
    val ordered = listOf(me).filter { it in ids } + ids.filter { it != me }
    return MembersState(
        me = me,
        members = ordered.map { uid ->
            MemberEntry(
                person = personOf(uid, me, emptyList(), ids, members),
                isOwner = uid == ownerId,
                joinedOn = members.firstOrNull { it.uid == uid }?.joinedAt
                    ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
            )
        },
        isOwner = me == ownerId,
    )
}
