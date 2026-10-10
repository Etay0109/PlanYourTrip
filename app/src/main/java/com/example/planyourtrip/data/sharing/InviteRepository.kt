package com.example.planyourtrip.data.sharing

/**
 * Invites and joining (the cloud only; nothing is stored on the device). The real implementation is
 * [FirestoreInviteRepository]; firestore.rules enforce every condition, the app only words the outcome.
 * Everything throws [InviteUnavailableException] when offline or when a request fails, except [join] (a result).
 */
interface InviteRepository {
    /** The owner's open invite for trip [tripUuid], or null when there is none (used, cancelled or expired). */
    suspend fun currentInvite(uid: String, tripUuid: String): Invite?

    /**
     * A new invite for trip [tripUuid] by its owner [uid], whose member document gets [ownerName]. Throws
     * [TripFullException] when the trip has 10 members.
     */
    suspend fun createInvite(uid: String, tripUuid: String, ownerName: String): Invite

    /** The owner cancels [code]: it can't be read or used any more. */
    suspend fun cancelInvite(uid: String, tripUuid: String, code: String)

    /** What the open invite [code] shows, or null when the code isn't valid (wrong, expired, used, cancelled). */
    suspend fun lookUp(uid: String, code: String): InvitePreview?

    /** [uid] joins [preview]'s trip with its code, as [name] (a display name, never an email). */
    suspend fun join(uid: String, preview: InvitePreview, name: String): JoinResult
}
