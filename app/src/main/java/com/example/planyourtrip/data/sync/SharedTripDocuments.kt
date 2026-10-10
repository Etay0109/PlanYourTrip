package com.example.planyourtrip.data.sync

/**
 * Paths and field names of the shared trips' membership documents (see firestore.rules): the member documents
 * trips/{trip}/members/{uid}, the owner's pointer trips/{trip}/private/invite and the invites invites/{code}. Used by
 * FirestoreCloudStore and FirestoreInviteRepository; the rules check exactly these keys (`hasOnly`).
 */
object SharedTripDocuments {
    const val TRIPS = "trips"
    const val MEMBERS = "members"
    const val PRIVATE = "private"
    const val INVITE_POINTER = "invite"
    const val INVITES = "invites"

    /** trips/{trip}/members/{uid}: display name (never an email), join time (server), left, the code used. */
    object Member {
        const val NAME = "name"
        const val JOINED_AT = "joinedAt"
        const val LEFT = "left"
        const val INVITE_CODE = "inviteCode"
    }

    /** trips/{trip}/private/invite: the owner's current code (or null). */
    const val POINTER_CODE = "code"

    /** invites/{code}. */
    object Invite {
        const val TRIP_ID = "tripId"
        const val INVITER_NAME = "inviterName"
        const val DESTINATION = "destination"
        const val START_DATE = "startDate"
        const val END_DATE = "endDate"
        const val CREATED_AT = "createdAt"
        const val STATUS = "status"
        const val USED_BY = "usedBy"

        const val OPEN = "open"
        const val USED = "used"
        const val CANCELLED = "cancelled"
    }

    /** Firestore allows at most 10 members per trip (firestore.rules `validMembers`). */
    const val MAX_MEMBERS = 10
}
