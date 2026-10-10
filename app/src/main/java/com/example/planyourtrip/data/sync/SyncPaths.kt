package com.example.planyourtrip.data.sync

/**
 * Where each sync item lives in Firestore (pure, so it is unit-tested): the shared trip trips/{trip}, its shared
 * children trips/{trip}/{collection}/{uuid}, and the member's personal items trips/{trip}/memberData/{uid} (budget)
 * and …/memberData/{uid}/packing/{uuid}. FirestoreCloudStore writes and reads exactly these paths; FakeCloudStore
 * stores its documents under them too.
 */
object SyncPaths {
    const val TRIPS = SharedTripDocuments.TRIPS
    const val MEMBER_DATA = "memberData"
    const val PACKING = "packing"

    /** The shared children of a trip: kind → subcollection (also the legacy copy's, where both checklists share one). */
    val SHARED_CHILDREN: List<Pair<SyncKind, String>> = listOf(
        SyncKind.TRAVELER to "travelers",
        SyncKind.EXPENSE to "expenses",
        SyncKind.ACTIVITY to "activities",
        SyncKind.CHECKLIST to "checklist",
        SyncKind.SETTLEMENT to "settlements",
    )

    /** The document [uid] writes [item] to, as path segments ("trips", trip uuid, …, document id). */
    fun of(uid: String, item: SyncItem): List<String> {
        val trip = listOf(TRIPS, item.tripUuid ?: item.uuid)
        return when (item.kind) {
            SyncKind.TRIP -> trip
            SyncKind.BUDGET -> trip + listOf(MEMBER_DATA, uid)
            SyncKind.PACKING -> trip + listOf(MEMBER_DATA, uid, PACKING, item.uuid)
            else -> trip + listOf(SHARED_CHILDREN.first { it.first == item.kind }.second, item.uuid)
        }
    }

    /** The trip a document at [path] belongs to: null for the trip document itself. */
    fun tripUuidOf(path: List<String>): String? = path[1].takeIf { path.size > 2 }

    /** The document id at [path] (its last segment), what Firestore's `DocumentSnapshot.id` returns. */
    fun documentIdOf(path: List<String>): String = path.last()
}
