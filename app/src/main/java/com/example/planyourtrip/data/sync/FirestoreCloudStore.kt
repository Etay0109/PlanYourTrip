package com.example.planyourtrip.data.sync

import android.util.Log
import com.example.planyourtrip.data.trip.TripMember
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Cloud Firestore, shared trips: trips/{tripUuid} (with `ownerId` and `memberIds`), its shared children
 * …/expenses, …/activities, …/checklist ("Before the trip"), …/travelers, …/settlements, and each member's personal
 * items …/memberData/{uid} (budget) and …/memberData/{uid}/packing. The legacy copy (users/{uid}/trips/…) is only
 * read and deleted by the one-time migration ([LegacyMigration]).
 * The only class in the app that imports Firestore. Field names and tombstones come from [SyncDocuments] (see
 * firestore.rules).
 */
class FirestoreCloudStore private constructor(private val firestore: FirebaseFirestore) : CloudStore {

    override suspend fun fetchAll(uid: String): List<SyncItem> = call("fetchAll") {
        // The rules allow only this query: trips whose members include the signed-in account.
        val tripDocs = trips().whereArrayContains(SyncDocuments.MEMBER_IDS, uid).get(Source.SERVER).await().documents
        coroutineScope {
            tripDocs.map { doc ->
                async {
                    val trip = read(SyncKind.TRIP, doc, tripUuid = null) ?: return@async emptyList()
                    // A deleted trip's children are tombstones too; no need to read them.
                    if (trip.isDeleted) return@async listOf(trip)
                    val shared = SHARED_CHILDREN.map { (kind, name) ->
                        kind to doc.reference.collection(name).get(Source.SERVER)
                    }
                    val mine = memberData(doc.id, uid)
                    val budget = mine.get(Source.SERVER)
                    val packing = mine.collection(PACKING).get(Source.SERVER)
                    val sharedItems = shared.flatMap { (kind, task) ->
                        task.await().documents.mapNotNull { read(kind, it, doc.id) }
                    }
                    val myBudget = budget.await().takeIf { it.exists() }?.let { read(SyncKind.BUDGET, it, doc.id) }
                    listOf(trip) + sharedItems + listOfNotNull(myBudget) +
                        packing.await().documents.mapNotNull { read(SyncKind.PACKING, it, doc.id) }
                }
            }.awaitAll().flatten()
        }
    }

    override suspend fun fetch(uid: String, items: List<SyncItem>): List<SyncItem> = call("fetch") {
        coroutineScope {
            // Trips first: a child can only be read while its trip exists (the rules check membership on the trip).
            val tripUuids = items.map { it.tripUuid ?: it.uuid }.distinct()
            val tripDocs = tripUuids.map { id -> async { id to trips().document(id).get(Source.SERVER).await() } }
                .awaitAll().toMap()
            items.map { item ->
                async {
                    val tripDoc = tripDocs.getValue(item.tripUuid ?: item.uuid)
                    when {
                        !tripDoc.exists() -> null
                        item.kind == SyncKind.TRIP -> read(SyncKind.TRIP, tripDoc, null)
                        else -> ref(uid, item).get(Source.SERVER).await().takeIf { it.exists() }
                            ?.let { read(item.kind, it, item.tripUuid) }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    override suspend fun write(uid: String, items: List<SyncItem>) = call("write") {
        // Trips first, so a new trip exists (in the same or an earlier batch) before its children are written.
        val sorted = items.sortedBy { it.kind.ordinal }
        val existingTrips = coroutineScope {
            sorted.filter { it.kind == SyncKind.TRIP }
                .map { trip -> async { trip.uuid.takeIf { trips().document(it).get(Source.SERVER).await().exists() } } }
                .awaitAll().filterNotNull().toSet()
        }
        // One batch (or more, for a big trip) per trip: the rules may look up only a few trips per request.
        val writes = mutableMapOf<String, MutableList<(WriteBatch) -> Unit>>()
        for (item in sorted) {
            val ref = ref(uid, item)
            val tripWrites = writes.getOrPut(item.tripUuid ?: item.uuid) { mutableListOf() }
            if (item.kind != SyncKind.TRIP) {
                // A tombstone replaces the whole document (no merge), so the data fields are removed.
                val fields = SyncDocuments.toDocument(item)
                tripWrites += { batch -> batch.set(ref, fields) }
                continue
            }
            val exists = item.uuid in existingTrips
            when {
                // A new trip: the signed-in account is its owner and only member.
                !exists -> {
                    val fields = SyncDocuments.toDocument(item) +
                        mapOf(SyncDocuments.OWNER_ID to uid, SyncDocuments.MEMBER_IDS to listOf(uid))
                    tripWrites += { batch -> batch.set(ref, fields) }
                }
                // An existing trip: only the data fields change, never the owner or the members (a stale local
                // member list must not undo a membership change made elsewhere).
                !item.isDeleted -> {
                    val fields = SyncDocuments.toDocument(item) - SyncDocuments.OWNER_ID - SyncDocuments.MEMBER_IDS
                    tripWrites += { batch -> batch.set(ref, fields, SetOptions.merge()) }
                }
                // Deleted: the data fields go, owner and members stay (so members still learn about the delete).
                else -> {
                    val fields = SyncDocuments.TRIP_DATA_FIELDS.associateWith { FieldValue.delete() } +
                        SyncDocuments.toDocument(item)
                    tripWrites += { batch -> batch.update(ref, fields) }
                }
            }
            if (item.isDeleted && exists) {
                // Other devices must also drop the trip's children; my own personal items go too.
                val trip = trips().document(item.uuid)
                val children = SHARED_CHILDREN.map { trip.collection(it.second) } +
                    memberData(item.uuid, uid).collection(PACKING)
                val tombstone = SyncDocuments.toDocument(item.copy(data = null))
                for (child in children) {
                    child.get(Source.SERVER).await().documents
                        .filter { it.getBoolean(SyncDocuments.DELETED) != true }
                        .forEach { doc -> tripWrites += { batch -> batch.set(doc.reference, tombstone) } }
                }
                val budget = memberData(item.uuid, uid).get(Source.SERVER).await()
                if (budget.exists() && budget.getBoolean(SyncDocuments.DELETED) != true) {
                    tripWrites += { batch -> batch.set(budget.reference, tombstone) }
                }
            }
        }
        writes.values.forEach { commit(it) }
    }

    override suspend fun tripAccess(uid: String, tripUuids: Collection<String>): Map<String, TripAccess> =
        call("tripAccess") {
            coroutineScope {
                tripUuids.map { id -> async { id to access(id) } }.awaitAll().toMap()
            }
        }

    /** Only a denied read means "no access"; any other failure (offline, timeout) fails the sync. */
    private suspend fun access(tripUuid: String): TripAccess =
        try {
            val doc = trips().document(tripUuid).get(Source.SERVER).await()
            when {
                !doc.exists() -> TripAccess.MISSING
                doc.getBoolean(SyncDocuments.DELETED) == true -> TripAccess.DELETED
                else -> TripAccess.LIVE
            }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) TripAccess.NO_ACCESS else throw e
        }

    override suspend fun fetchMembers(uid: String, tripUuids: Collection<String>): Map<String, List<TripMember>> =
        call("fetchMembers") {
            coroutineScope {
                tripUuids.map { id ->
                    async {
                        val docs = trips().document(id).collection(SharedTripDocuments.MEMBERS).get(Source.SERVER)
                            .await().documents
                        id to docs.mapNotNull { readMember(it) }
                    }
                }.awaitAll().toMap()
            }
        }

    private fun readMember(doc: DocumentSnapshot): TripMember? {
        val name = doc.getString(SharedTripDocuments.Member.NAME)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return null.also { Log.w(TAG, "Skipping unreadable member document ${doc.reference.path}") }
        return TripMember(
            uid = doc.id,
            name = name,
            joinedAt = doc.getTimestamp(SharedTripDocuments.Member.JOINED_AT)?.toDate()?.time,
            left = doc.getBoolean(SharedTripDocuments.Member.LEFT) == true,
        )
    }

    override suspend fun deletePersonal(uid: String, tripUuid: String) = call("deletePersonal") {
        val mine = memberData(tripUuid, uid)
        val packing = mine.collection(PACKING).get(Source.SERVER).await().documents
        // Packing items first, so an interruption never leaves items without their budget document's delete pending.
        val refs = packing.map { it.reference } + mine
        commit(refs.map { ref -> { batch: WriteBatch -> batch.delete(ref); Unit } })
    }

    override suspend fun leaveTrip(uid: String, tripUuid: String) = call("leaveTrip") {
        val trip = trips().document(tripUuid)
        val mine = memberData(tripUuid, uid)
        val member = trip.collection(SharedTripDocuments.MEMBERS).document(uid)
        val packing = mine.collection(PACKING).get(Source.SERVER).await().documents
        val hasMemberDoc = member.get(Source.SERVER).await().exists()
        // The leave write holds the trip, my member document, my budget and as many Packing items as fit; any others
        // are deleted just before it (deleting my own personal data is always allowed).
        val room = MAX_BATCH - 3
        commit(packing.drop(room).map { doc -> { batch: WriteBatch -> batch.delete(doc.reference); Unit } })
        val batch = firestore.batch()
        batch.update(trip, SyncDocuments.MEMBER_IDS, FieldValue.arrayRemove(uid))
        if (hasMemberDoc) batch.update(member, SharedTripDocuments.Member.LEFT, true)
        batch.delete(mine)
        packing.take(room).forEach { batch.delete(it.reference) }
        batch.commit().await()
        Unit
    }

    override suspend fun removeMember(uid: String, tripUuid: String, memberUid: String) = call("removeMember") {
        val trip = trips().document(tripUuid)
        val member = trip.collection(SharedTripDocuments.MEMBERS).document(memberUid)
        val hasMemberDoc = member.get(Source.SERVER).await().exists()
        val batch = firestore.batch()
        batch.update(trip, SyncDocuments.MEMBER_IDS, FieldValue.arrayRemove(memberUid))
        if (hasMemberDoc) batch.update(member, SharedTripDocuments.Member.LEFT, true)
        batch.commit().await()
        Unit
    }

    override suspend fun fetchLegacy(uid: String): List<SyncItem> = call("fetchLegacy") {
        val tripDocs = legacyList(legacyTrips(uid))
        coroutineScope {
            tripDocs.map { doc ->
                async {
                    val trip = read(SyncKind.TRIP, doc, tripUuid = null) ?: return@async emptyList()
                    val budget = SyncDocuments.legacyBudget(doc.id, doc.data.orEmpty())
                    val children = LEGACY_CHILDREN.flatMap { (kind, name) ->
                        legacyList(doc.reference.collection(name)).mapNotNull { read(kind, it, doc.id) }
                    }
                    listOf(trip) + listOfNotNull(budget) + children
                }
            }.awaitAll().flatten()
        }
    }

    override suspend fun markLegacyMigrated(uid: String) = call("markLegacyMigrated") {
        firestore.collection(USERS).document(uid).set(mapOf(MIGRATED to true)).await()
        Unit
    }

    override suspend fun deleteLegacy(uid: String) = call("deleteLegacy") {
        val tripDocs = legacyList(legacyTrips(uid))
        val children = coroutineScope {
            tripDocs.map { trip ->
                async {
                    (LEGACY_CHILDREN.map { it.second } + LEGACY_PLACES)
                        .flatMap { legacyList(trip.reference.collection(it)) }
                }
            }.awaitAll().flatten()
        }
        // Children first, so an interruption never leaves children without their trip document.
        commit((children + tripDocs).map { doc -> { batch: WriteBatch -> batch.delete(doc.reference) } })
    }

    /**
     * Every document of a legacy collection, read in pages of [LEGACY_PAGE]. The rules let old app versions (which
     * read without a limit) list the legacy copy only until it is migrated; this query keeps working, so leftovers
     * of an interrupted migration can still be found and deleted.
     */
    private suspend fun legacyList(collection: CollectionReference): List<DocumentSnapshot> {
        val all = mutableListOf<DocumentSnapshot>()
        var query: Query = collection.orderBy(FieldPath.documentId()).limit(LEGACY_PAGE)
        while (true) {
            val page = query.get(Source.SERVER).await().documents
            all += page
            if (page.size < LEGACY_PAGE) return all
            query = collection.orderBy(FieldPath.documentId())
                .startAfter(page.last()).limit(LEGACY_PAGE)
        }
    }

    private suspend fun commit(writes: List<(WriteBatch) -> Unit>) {
        for (chunk in writes.chunked(MAX_BATCH)) {
            val batch = firestore.batch()
            chunk.forEach { it(batch) }
            batch.commit().await()
        }
    }

    private fun trips(): CollectionReference = firestore.collection(TRIPS)

    private fun memberData(tripUuid: String, uid: String): DocumentReference =
        trips().document(tripUuid).collection(MEMBER_DATA).document(uid)

    private fun legacyTrips(uid: String): CollectionReference =
        firestore.collection(USERS).document(uid).collection(TRIPS)

    private fun ref(uid: String, item: SyncItem): DocumentReference =
        firestore.document(SyncPaths.of(uid, item).joinToString("/"))

    /** [tripUuid] = the trip the document belongs to (null for a trip document); see [SyncDocuments.fromDocument]. */
    private fun read(kind: SyncKind, doc: DocumentSnapshot, tripUuid: String?): SyncItem? =
        SyncDocuments.fromDocument(kind, doc.id, tripUuid, doc.data.orEmpty())
            ?: null.also { Log.w(TAG, "Skipping unreadable ${kind.name} document ${doc.reference.path}") }

    /**
     * Every failure becomes a [CloudUnavailableException]; the sync then keeps the changes pending. Only "the server
     * can't be reached" counts as a connection failure; anything else (denied by the rules, invalid data, quota) is a
     * sync problem the user should see.
     */
    private suspend fun <T> call(name: String, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$name failed", e)
            val connection = e is FirebaseFirestoreException && e.code in CONNECTION_FAILURES
            throw CloudUnavailableException(e, isProblem = !connection)
        }

    companion object {
        private const val TAG = "CloudSync"
        private const val USERS = "users"
        private const val TRIPS = SyncPaths.TRIPS
        private const val MEMBER_DATA = SyncPaths.MEMBER_DATA
        private const val PACKING = SyncPaths.PACKING

        /** Failures that mean the server couldn't be reached (offline, timed out on the server's side). */
        private val CONNECTION_FAILURES = setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
        )

        /** The legacy copy's migration marker in users/{uid}. */
        private const val MIGRATED = "sharedTripsMigrated"

        /** Places were synced until the Map tab was removed; only [deleteLegacy] still touches them. */
        private const val LEGACY_PLACES = "places"

        /** Page size of the legacy queries; firestore.rules accept exactly this limit after the migration. */
        private const val LEGACY_PAGE = 450L

        /** The shared children of a trip: kind → subcollection. */
        private val SHARED_CHILDREN = SyncPaths.SHARED_CHILDREN

        /** The legacy copy kept both checklists in one subcollection. */
        private val LEGACY_CHILDREN = SHARED_CHILDREN

        /** Firestore allows at most 500 writes per batch. */
        private const val MAX_BATCH = 450

        /**
         * Memory cache only: Room is the one copy on the device, so signing out really removes the account's
         * data, and reads always come from the server ([Source.SERVER]; offline fails fast).
         */
        fun create(firestore: FirebaseFirestore): FirestoreCloudStore {
            firestore.firestoreSettings = firestoreSettings { setLocalCacheSettings(memoryCacheSettings {}) }
            return FirestoreCloudStore(firestore)
        }
    }
}
