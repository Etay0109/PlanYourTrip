package com.example.planyourtrip

import com.example.planyourtrip.data.sync.CloudStore
import com.example.planyourtrip.data.sync.CloudUnavailableException
import com.example.planyourtrip.data.sync.SyncDocuments
import com.example.planyourtrip.data.sync.SyncItem
import com.example.planyourtrip.data.sync.SyncKind
import com.example.planyourtrip.data.sync.SyncPaths
import com.example.planyourtrip.data.sync.TripAccess
import com.example.planyourtrip.data.trip.TripMember

/**
 * In-memory [CloudStore] that holds **Firestore documents under Firestore's paths**, never Firestore: every item is
 * written to [SyncPaths.of] as [SyncDocuments.toDocument] fields and read back with [SyncDocuments.fromDocument] and
 * that document's own id, exactly like FirestoreCloudStore. So a budget lives in trips/{trip}/memberData/{uid} (its
 * id is the member's uid), and a mistake in a path or in reading an id shows up in the sync tests.
 *
 * Like FirestoreCloudStore and the rules: a new trip is created with the writing account as owner and only member;
 * an existing trip's owner and members never change through [write] (only its data fields); a trip tombstone keeps
 * them and turns its shared children and the writer's own personal items into tombstones; only members write a
 * trip's items. [fetchAll] returns a deleted trip without its children.
 *
 * Set [offline] to fail every call, or [failAfterWrites] to let that many more write calls succeed and fail the rest
 * (offline in the middle). [legacy] is each account's legacy copy (users/{uid}/trips) as FirestoreCloudStore's
 * fetchLegacy returns it (budgets already as BUDGET items, Packing items as CHECKLIST); [legacyMigrated] the accounts
 * whose marker is set; [failLegacyDelete] fails only the legacy delete.
 *
 * Membership: [memberDocs] are the trips' member documents; [leaveTrip], [removeMember] and [removeFromTrip] (the
 * owner removing someone from another device) change the members like the rules allow. [failList] fails the
 * "trips where I am a member" query; [denyTripReads] makes every direct trip read denied (rules gone wrong);
 * [failDeletePersonal] fails only [deletePersonal].
 *
 * [unreadableKinds]: documents of these kinds are stored but never come back when read (FirestoreCloudStore skips a
 * document it can't read). With [SyncKind.BUDGET] this is what the budget-key bug did on the phone: every budget
 * looked missing, so the legacy migration could never finish.
 */
class FakeCloudStore : CloudStore {
    /** Document path ("trips/t1/expenses/e1") → its fields, as Firestore stores them. */
    private val documents = linkedMapOf<String, Map<String, Any?>>()

    var offline = false
    var failAfterWrites: Int? = null

    /** uid → the legacy copy as stored (keys "me", budget as BUDGET items, Packing items as CHECKLIST). */
    val legacy = mutableMapOf<String, MutableList<SyncItem>>()
    val legacyMigrated = mutableSetOf<String>()
    var failLegacyDelete = false

    /** trip uuid → uid → member document. */
    val memberDocs = mutableMapOf<String, MutableMap<String, TripMember>>()
    var failList = false
    var denyTripReads = false
    var failDeletePersonal = false
    var unreadableKinds: Set<SyncKind> = emptySet()

    /** Accounts whose personal data of a trip was deleted: (uid, trip uuid). */
    val deletedPersonal = mutableListOf<Pair<String, String>>()

    /** Runs inside each successful write, before it returns (e.g. to make a local edit "during" the sync). */
    var onWrite: suspend () -> Unit = {}

    val calls = mutableListOf<String>()

    /**
     * What [uid] can see, read back from the documents and keyed by uuid (a trip's budget under "budget:<item uuid>"):
     * the trips they are a member of with all their shared children (also a deleted trip's tombstoned ones), and their
     * own personal items.
     */
    fun docsOf(uid: String): Map<String, SyncItem> =
        myTrips(uid).flatMap { trip -> listOfNotNull(readTrip(trip)) + childrenOf(trip, uid) }
            .associateBy { if (it.kind == SyncKind.BUDGET) "budget:${it.uuid}" else it.uuid }

    /** The members of trip [tripUuid] (owner first), or null when the trip isn't in the cloud. */
    fun membersOf(tripUuid: String): List<String>? = documents[tripPath(tripUuid)]?.let(::memberIdsOf)

    /** Adds [newMember] to a trip, as joining does (FirestoreInviteRepository.join). */
    fun addMember(tripUuid: String, newMember: String) {
        val doc = documents.getValue(tripPath(tripUuid))
        documents[tripPath(tripUuid)] = doc + (SyncDocuments.MEMBER_IDS to memberIdsOf(doc) + newMember)
    }

    /** The owner (or anyone, from another device) takes [uid] out of the trip's members, marking them as left. */
    fun removeFromTrip(tripUuid: String, uid: String) {
        val doc = documents.getValue(tripPath(tripUuid))
        documents[tripPath(tripUuid)] = doc + (SyncDocuments.MEMBER_IDS to memberIdsOf(doc) - uid)
        memberDocs[tripUuid]?.get(uid)?.let { memberDocs.getValue(tripUuid)[uid] = it.copy(left = true) }
    }

    /** The personal items (budget, Packing) [uid] has in the cloud for trip [tripUuid]. */
    fun personalOf(uid: String, tripUuid: String): List<SyncItem> = personalItems(tripUuid, uid)

    /** Stores [items] as written by [uid] (no failure, no call recorded). */
    fun put(uid: String, vararg items: SyncItem) = store(uid, items.toList())

    override suspend fun fetchAll(uid: String): List<SyncItem> {
        calls += "fetchAll:$uid"
        check()
        if (failList) throw CloudUnavailableException()
        return myTrips(uid).flatMap { trip ->
            val item = readTrip(trip) ?: return@flatMap emptyList()
            // Like FirestoreCloudStore: a deleted trip's children are tombstones too, so they aren't read.
            if (item.isDeleted) listOf(item) else listOf(item) + childrenOf(trip, uid)
        }
    }

    override suspend fun fetch(uid: String, items: List<SyncItem>): List<SyncItem> {
        calls += "fetch:$uid:${items.size}"
        check()
        // Like Firestore: reading a trip this account isn't a member of is denied (the whole call fails).
        if (items.any { item -> documents[tripPath(item.tripKey)]?.let { uid !in memberIdsOf(it) } == true }) {
            throw CloudUnavailableException()
        }
        return items.mapNotNull { item ->
            if (tripPath(item.tripKey) !in documents) null else read(item.kind, SyncPaths.of(uid, item))
        }
    }

    override suspend fun write(uid: String, items: List<SyncItem>) {
        calls += "write:$uid:${items.size}"
        check()
        failAfterWrites?.let { left ->
            if (left <= 0) throw CloudUnavailableException()
            failAfterWrites = left - 1
        }
        // Like the rules: only members write a trip's items.
        items.forEach { item ->
            val trip = documents[tripPath(item.tripKey)]
            if (trip != null && uid !in memberIdsOf(trip)) throw CloudUnavailableException()
            if (trip == null && item.kind != SyncKind.TRIP && items.none { it.uuid == item.tripUuid }) {
                throw CloudUnavailableException()
            }
        }
        store(uid, items)
        onWrite()
    }

    override suspend fun tripAccess(uid: String, tripUuids: Collection<String>): Map<String, TripAccess> {
        calls += "tripAccess:$uid:${tripUuids.size}"
        check()
        return tripUuids.associateWith { id ->
            val trip = documents[tripPath(id)]
            when {
                denyTripReads -> TripAccess.NO_ACCESS
                trip == null -> TripAccess.MISSING
                uid !in memberIdsOf(trip) -> TripAccess.NO_ACCESS
                trip[SyncDocuments.DELETED] == true -> TripAccess.DELETED
                else -> TripAccess.LIVE
            }
        }
    }

    override suspend fun fetchMembers(uid: String, tripUuids: Collection<String>): Map<String, List<TripMember>> {
        calls += "fetchMembers:$uid"
        check()
        if (tripUuids.any { id -> documents[tripPath(id)]?.let { uid !in memberIdsOf(it) } != false }) {
            throw CloudUnavailableException()
        }
        return tripUuids.associateWith { memberDocs[it].orEmpty().values.toList() }
    }

    override suspend fun deletePersonal(uid: String, tripUuid: String) {
        calls += "deletePersonal:$uid:$tripUuid"
        check()
        if (failDeletePersonal) throw CloudUnavailableException()
        deletePersonalDocuments(uid, tripUuid)
        deletedPersonal += uid to tripUuid
    }

    override suspend fun leaveTrip(uid: String, tripUuid: String) {
        calls += "leaveTrip:$uid:$tripUuid"
        check()
        val trip = documents[tripPath(tripUuid)] ?: throw CloudUnavailableException()
        if (uid !in memberIdsOf(trip) || uid == trip[SyncDocuments.OWNER_ID]) throw CloudUnavailableException()
        removeFromTrip(tripUuid, uid)
        deletePersonalDocuments(uid, tripUuid)
    }

    override suspend fun removeMember(uid: String, tripUuid: String, memberUid: String) {
        calls += "removeMember:$uid:$tripUuid:$memberUid"
        check()
        val trip = documents[tripPath(tripUuid)] ?: throw CloudUnavailableException()
        if (trip[SyncDocuments.OWNER_ID] != uid || memberUid == uid || memberUid !in memberIdsOf(trip)) {
            throw CloudUnavailableException()
        }
        removeFromTrip(tripUuid, memberUid)
    }

    override suspend fun fetchLegacy(uid: String): List<SyncItem> {
        calls += "fetchLegacy:$uid"
        check()
        return legacy[uid].orEmpty().toList()
    }

    override suspend fun markLegacyMigrated(uid: String) {
        calls += "markLegacyMigrated:$uid"
        check()
        legacyMigrated += uid
    }

    override suspend fun deleteLegacy(uid: String) {
        calls += "deleteLegacy:$uid"
        check()
        if (failLegacyDelete) throw CloudUnavailableException()
        legacy.remove(uid)
    }

    // ---------- Documents, like FirestoreCloudStore writes and reads them ----------

    private fun store(uid: String, items: List<SyncItem>) {
        for (item in items.sortedBy { it.kind.ordinal }) {
            val path = SyncPaths.of(uid, item).joinToString("/")
            if (item.kind != SyncKind.TRIP) {
                // A tombstone replaces the whole document (no merge).
                documents[path] = SyncDocuments.toDocument(item)
                continue
            }
            val existing = documents[path]
            documents[path] = when {
                // A new trip: the writer is its owner and only member.
                existing == null -> SyncDocuments.toDocument(item) +
                    mapOf(SyncDocuments.OWNER_ID to uid, SyncDocuments.MEMBER_IDS to listOf(uid))
                // An existing trip: only the data fields change (merge), never the owner or the members.
                !item.isDeleted -> existing + (SyncDocuments.toDocument(item) - SyncDocuments.OWNER_ID - SyncDocuments.MEMBER_IDS)
                // Deleted: the data fields go, owner and members stay.
                else -> existing - SyncDocuments.TRIP_DATA_FIELDS.toSet() + SyncDocuments.toDocument(item)
            }
            if (item.isDeleted && existing != null) {
                // Other devices must also drop the trip's children; the writer's own personal items go too.
                val tombstone = SyncDocuments.toDocument(item.copy(data = null))
                val children = SyncPaths.SHARED_CHILDREN.map { "$path/${it.second}/" } +
                    "$path/${SyncPaths.MEMBER_DATA}/$uid/${SyncPaths.PACKING}/"
                documents.keys.filter { key -> children.any { key.startsWith(it) && '/' !in key.removePrefix(it) } }
                    .plus(listOf("$path/${SyncPaths.MEMBER_DATA}/$uid").filter { it in documents })
                    .filter { documents.getValue(it)[SyncDocuments.DELETED] != true }
                    .forEach { documents[it] = tombstone }
            }
        }
    }

    private fun read(kind: SyncKind, path: List<String>): SyncItem? {
        if (kind in unreadableKinds) return null
        val fields = documents[path.joinToString("/")] ?: return null
        return SyncDocuments.fromDocument(kind, SyncPaths.documentIdOf(path), SyncPaths.tripUuidOf(path), fields)
    }

    private fun readTrip(tripUuid: String): SyncItem? = read(SyncKind.TRIP, listOf(SyncPaths.TRIPS, tripUuid))

    /** The trip's shared children and [uid]'s own items of it, read from their documents. */
    private fun childrenOf(tripUuid: String, uid: String): List<SyncItem> =
        SyncPaths.SHARED_CHILDREN.flatMap { (kind, collection) -> readCollection(kind, listOf(SyncPaths.TRIPS, tripUuid, collection)) } +
            personalItems(tripUuid, uid)

    private fun personalItems(tripUuid: String, uid: String): List<SyncItem> {
        val mine = listOf(SyncPaths.TRIPS, tripUuid, SyncPaths.MEMBER_DATA, uid)
        return listOfNotNull(read(SyncKind.BUDGET, mine)) + readCollection(SyncKind.PACKING, mine + SyncPaths.PACKING)
    }

    /** Every document directly in the collection at [collection]. */
    private fun readCollection(kind: SyncKind, collection: List<String>): List<SyncItem> {
        val prefix = collection.joinToString("/") + "/"
        return documents.keys.filter { it.startsWith(prefix) && '/' !in it.removePrefix(prefix) }
            .mapNotNull { read(kind, it.split("/")) }
    }

    private fun deletePersonalDocuments(uid: String, tripUuid: String) {
        val mine = "${tripPath(tripUuid)}/${SyncPaths.MEMBER_DATA}/$uid"
        documents.keys.removeAll { it == mine || it.startsWith("$mine/") }
    }

    /** Trips [uid] is a member of (the only list query the rules allow). */
    private fun myTrips(uid: String): List<String> = documents.filter { (path, fields) ->
        path.count { it == '/' } == 1 && uid in memberIdsOf(fields)
    }.keys.map { it.substringAfter("/") }

    private fun memberIdsOf(trip: Map<String, Any?>): List<String> =
        (trip[SyncDocuments.MEMBER_IDS] as List<*>?).orEmpty().filterIsInstance<String>()

    private fun tripPath(tripUuid: String) = "${SyncPaths.TRIPS}/$tripUuid"

    private val SyncItem.tripKey: String get() = tripUuid ?: uuid

    private fun check() {
        if (offline) throw CloudUnavailableException()
    }
}
