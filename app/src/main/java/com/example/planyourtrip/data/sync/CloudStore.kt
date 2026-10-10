package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.trip.TripMember

/**
 * The cloud copy of the trips an account can see: shared trips under trips/{tripUuid}/… (the account is one of
 * the trip's members) and the account's own personal items under trips/{tripUuid}/memberData/{uid}/… . ViewModels
 * never see it; only [SyncManager] uses it. The real implementation is [FirestoreCloudStore]; tests use a fake.
 * Every failure (offline, timeout, permission) throws [CloudUnavailableException].
 */
interface CloudStore {
    /**
     * Every trip [uid] is a member of, with its shared children (travelers, expenses, activities, "Before the trip"
     * items, settlements) and [uid]'s own budget and Packing items; tombstones included.
     */
    suspend fun fetchAll(uid: String): List<SyncItem>

    /** The cloud versions of [items] (same kind, uuid and trip); items missing in the cloud are left out. */
    suspend fun fetch(uid: String, items: List<SyncItem>): List<SyncItem>

    /**
     * Writes [items] as they are: live items replace the document, deleted ones become tombstones. A trip that isn't
     * in the cloud yet is created with [uid] as its owner and only member; an existing trip's owner and members are
     * never changed. A trip tombstone also turns all its shared children and [uid]'s personal items into tombstones.
     */
    suspend fun write(uid: String, items: List<SyncItem>)

    // ---------- Membership ----------

    /** A direct read of each trip document (see [TripAccess]); a denied read is [TripAccess.NO_ACCESS], not a failure. */
    suspend fun tripAccess(uid: String, tripUuids: Collection<String>): Map<String, TripAccess>

    /** The member documents (trips/{trip}/members) of each trip, also of members who left. */
    suspend fun fetchMembers(uid: String, tripUuids: Collection<String>): Map<String, List<TripMember>>

    /**
     * Deletes [uid]'s own personal data of a trip (budget and every Packing item). Allowed by the rules even after
     * [uid] left or was removed.
     */
    suspend fun deletePersonal(uid: String, tripUuid: String)

    /**
     * [uid] leaves the trip, in one write: out of its members, their member document marked as left, and their budget
     * and Packing items deleted (items that don't fit into that write are deleted just before it).
     */
    suspend fun leaveTrip(uid: String, tripUuid: String)

    /** The owner [uid] removes [memberUid] from the trip: out of its members, member document marked as left. */
    suspend fun removeMember(uid: String, tripUuid: String, memberUid: String)

    // ---------- The legacy copy (users/{uid}/trips/…, before shared trips), see LegacyMigration ----------

    /**
     * Every item of [uid]'s legacy copy as it was stored (keys "me", budget as a [SyncKind.BUDGET] item with the
     * trip's `updatedAt`, Packing items as [SyncKind.CHECKLIST]); tombstones included. Old place documents are not
     * read.
     */
    suspend fun fetchLegacy(uid: String): List<SyncItem>

    /** Marks [uid]'s legacy copy as migrated: from now on old app versions can't read it (firestore.rules). */
    suspend fun markLegacyMigrated(uid: String)

    /** Deletes every document of [uid]'s legacy copy (old place documents included). */
    suspend fun deleteLegacy(uid: String)
}

/**
 * A cloud call or a sync step failed; nothing local was lost. [isProblem] = false: the cloud couldn't be reached
 * (offline, timeout), it will work again by itself. True: something else went wrong (refused by the rules, a
 * migration that can't finish, …); the profile menu shows "Sync problem" until a full sync succeeds.
 */
class CloudUnavailableException(
    cause: Throwable? = null,
    val isProblem: Boolean = false,
    message: String? = cause?.toString(),
) : Exception(message, cause)
