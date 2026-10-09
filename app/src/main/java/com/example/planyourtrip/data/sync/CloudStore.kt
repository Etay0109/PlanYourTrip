package com.example.planyourtrip.data.sync

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

class CloudUnavailableException(cause: Throwable? = null) : Exception(cause)
