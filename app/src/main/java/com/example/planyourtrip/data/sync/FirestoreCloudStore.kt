package com.example.planyourtrip.data.sync

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Cloud Firestore: users/{uid}/trips/{tripUuid}, …/expenses/{uuid}, …/activities/{uuid}, …/checklist/{uuid}.
 * The only class in the app that imports Firestore. Field names and tombstones come from [SyncDocuments] (see firestore.rules).
 */
class FirestoreCloudStore private constructor(private val firestore: FirebaseFirestore) : CloudStore {

    override suspend fun fetchAll(uid: String): List<SyncItem> = call("fetchAll") {
        val tripDocs = trips(uid).get(Source.SERVER).await().documents
        coroutineScope {
            tripDocs.map { doc ->
                async {
                    val trip = read(SyncKind.TRIP, doc, tripUuid = null) ?: return@async emptyList()
                    // A deleted trip's children are tombstones too; no need to read them.
                    if (trip.isDeleted) return@async listOf(trip)
                    val expenses = doc.reference.collection(EXPENSES).get(Source.SERVER)
                    val activities = doc.reference.collection(ACTIVITIES).get(Source.SERVER)
                    val checklist = doc.reference.collection(CHECKLIST).get(Source.SERVER)
                    listOf(trip) +
                        expenses.await().documents.mapNotNull { read(SyncKind.EXPENSE, it, doc.id) } +
                        activities.await().documents.mapNotNull { read(SyncKind.ACTIVITY, it, doc.id) } +
                        checklist.await().documents.mapNotNull { read(SyncKind.CHECKLIST, it, doc.id) }
                }
            }.awaitAll().flatten()
        }
    }

    override suspend fun fetch(uid: String, items: List<SyncItem>): List<SyncItem> = call("fetch") {
        coroutineScope {
            items.map { item ->
                async {
                    val doc = ref(uid, item).get(Source.SERVER).await()
                    if (doc.exists()) read(item.kind, doc, item.tripUuid) else null
                }
            }.awaitAll().filterNotNull()
        }
    }

    override suspend fun write(uid: String, items: List<SyncItem>) = call("write") {
        val writes = mutableListOf<Pair<DocumentReference, Map<String, Any?>>>()
        for (item in items) {
            writes += ref(uid, item) to SyncDocuments.toDocument(item)
            if (item.kind == SyncKind.TRIP && item.isDeleted) {
                // Other devices must also drop the trip's expenses, activities and checklist items.
                val trip = trips(uid).document(item.uuid)
                for (child in listOf(EXPENSES, ACTIVITIES, CHECKLIST)) {
                    trip.collection(child).get(Source.SERVER).await().documents
                        .filter { it.getBoolean(SyncDocuments.DELETED) != true }
                        .forEach { writes += it.reference to SyncDocuments.toDocument(item.copy(data = null)) }
                }
            }
        }
        // A tombstone replaces the whole document (no merge), so the data fields are removed.
        for (chunk in writes.chunked(MAX_BATCH)) {
            val batch = firestore.batch()
            chunk.forEach { (ref, fields) -> batch.set(ref, fields) }
            batch.commit().await()
        }
    }

    override suspend fun deleteLegacyPlaces(uid: String) = call("deleteLegacyPlaces") {
        val tripDocs = trips(uid).get(Source.SERVER).await().documents
        val places = coroutineScope {
            tripDocs.map { trip ->
                async { trip.reference.collection(LEGACY_PLACES).get(Source.SERVER).await().documents }
            }.awaitAll().flatten()
        }
        for (chunk in places.chunked(MAX_BATCH)) {
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private fun trips(uid: String): CollectionReference =
        firestore.collection(USERS).document(uid).collection(TRIPS)

    private fun ref(uid: String, item: SyncItem): DocumentReference = when (item.kind) {
        SyncKind.TRIP -> trips(uid).document(item.uuid)
        SyncKind.EXPENSE -> trips(uid).document(checkNotNull(item.tripUuid)).collection(EXPENSES).document(item.uuid)
        SyncKind.ACTIVITY -> trips(uid).document(checkNotNull(item.tripUuid)).collection(ACTIVITIES).document(item.uuid)
        SyncKind.CHECKLIST -> trips(uid).document(checkNotNull(item.tripUuid)).collection(CHECKLIST).document(item.uuid)
    }

    private fun read(kind: SyncKind, doc: DocumentSnapshot, tripUuid: String?): SyncItem? =
        SyncDocuments.fromDocument(kind, doc.id, tripUuid, doc.data.orEmpty())
            ?: null.also { Log.w(TAG, "Skipping unreadable ${kind.name} document ${doc.reference.path}") }

    /** Every failure becomes a [CloudUnavailableException]; the sync then keeps the changes pending. */
    private suspend fun <T> call(name: String, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "$name failed", e)
            throw CloudUnavailableException(e)
        }

    companion object {
        private const val TAG = "CloudSync"
        private const val USERS = "users"
        private const val TRIPS = "trips"
        private const val EXPENSES = "expenses"
        /** Places were synced until the Map tab was removed; only [deleteLegacyPlaces] still touches them. */
        private const val LEGACY_PLACES = "places"
        private const val ACTIVITIES = "activities"
        private const val CHECKLIST = "checklist"

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
