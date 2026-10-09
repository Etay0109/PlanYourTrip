package com.example.planyourtrip

import com.example.planyourtrip.data.sync.CloudStore
import com.example.planyourtrip.data.sync.CloudUnavailableException
import com.example.planyourtrip.data.sync.ItemData
import com.example.planyourtrip.data.sync.SyncItem
import com.example.planyourtrip.data.sync.SyncKind

/**
 * In-memory [CloudStore] laid out like Firestore's shared trips, never Firestore: trips with an owner and members,
 * their shared children, and each member's personal items (budget, Packing) that only that member sees. Like the
 * real store, a new trip is created with the writing account as owner and only member, and an existing trip's
 * owner and members never change through [write].
 *
 * Set [offline] to fail every call, or [failAfterWrites] to let that many more write calls succeed and fail the rest
 * (offline in the middle). [legacy] is each account's legacy copy (users/{uid}/trips, before shared trips);
 * [legacyMigrated] the accounts whose marker is set; [failLegacyDelete] fails only the legacy delete.
 */
class FakeCloudStore : CloudStore {
    private data class TripDoc(val item: SyncItem, val ownerId: String, val memberIds: List<String>)

    private val trips = mutableMapOf<String, TripDoc>()

    /** Shared children: (kind, uuid) → document. */
    private val shared = mutableMapOf<Pair<SyncKind, String>, SyncItem>()

    /** uid → (kind, uuid) → personal document. */
    private val personal = mutableMapOf<String, MutableMap<Pair<SyncKind, String>, SyncItem>>()

    var offline = false
    var failAfterWrites: Int? = null

    /** uid → the legacy copy as stored (keys "me", budget as BUDGET items, Packing items as CHECKLIST). */
    val legacy = mutableMapOf<String, MutableList<SyncItem>>()
    val legacyMigrated = mutableSetOf<String>()
    var failLegacyDelete = false

    /** Runs inside each successful write, before it returns (e.g. to make a local edit "during" the sync). */
    var onWrite: suspend () -> Unit = {}

    val calls = mutableListOf<String>()

    /**
     * What [uid] can see, by uuid (a trip's budget under "budget:<trip uuid>"): the trips they are a member of with
     * their shared children, and their own personal items.
     */
    fun docsOf(uid: String): Map<String, SyncItem> = visibleTo(uid).associateBy { keyOf(it) }

    /** The members of trip [tripUuid] (owner first), or null when the trip isn't in the cloud. */
    fun membersOf(tripUuid: String): List<String>? = trips[tripUuid]?.memberIds

    /** Adds [newMember] to a trip, as joining will (Stage 2); for tests of shared trips. */
    fun addMember(tripUuid: String, newMember: String) {
        val doc = trips.getValue(tripUuid)
        trips[tripUuid] = doc.copy(memberIds = doc.memberIds + newMember).withAccess()
    }

    /** Stores [items] as written by [uid] (no failure, no call recorded). */
    fun put(uid: String, vararg items: SyncItem) = store(uid, items.toList())

    override suspend fun fetchAll(uid: String): List<SyncItem> {
        calls += "fetchAll:$uid"
        check()
        return visibleTo(uid)
    }

    override suspend fun fetch(uid: String, items: List<SyncItem>): List<SyncItem> {
        calls += "fetch:$uid:${items.size}"
        check()
        val visible = visibleTo(uid).associateBy { it.kind to it.uuid }
        return items.mapNotNull { visible[it.kind to it.uuid] }
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
            val trip = trips[item.tripUuid ?: item.uuid]
            if (trip != null && uid !in trip.memberIds) throw CloudUnavailableException()
            if (trip == null && item.kind != SyncKind.TRIP && items.none { it.uuid == item.tripUuid }) {
                throw CloudUnavailableException()
            }
        }
        store(uid, items)
        onWrite()
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

    private fun store(uid: String, items: List<SyncItem>) {
        for (item in items.sortedBy { it.kind.ordinal }) {
            when (item.kind) {
                SyncKind.TRIP -> {
                    val existing = trips[item.uuid]
                    trips[item.uuid] = if (existing == null) {
                        TripDoc(item, ownerId = uid, memberIds = listOf(uid)).withAccess()
                    } else {
                        existing.copy(item = item).withAccess()
                    }
                    if (item.isDeleted) {
                        // Like Firestore: a trip tombstone turns its shared children and my own items into tombstones.
                        shared.filterValues { it.tripUuid == item.uuid && !it.isDeleted }
                            .forEach { (key, child) -> shared[key] = child.copy(updatedAt = item.updatedAt, data = null) }
                        val mine = personal[uid].orEmpty()
                        mine.filterValues { it.tripUuid == item.uuid && !it.isDeleted }
                            .forEach { (key, child) -> personal.getValue(uid)[key] = child.copy(updatedAt = item.updatedAt, data = null) }
                    }
                }
                SyncKind.BUDGET, SyncKind.PACKING -> personal.getOrPut(uid) { mutableMapOf() }[item.kind to item.uuid] = item
                else -> shared[item.kind to item.uuid] = item
            }
        }
    }

    /** The trip document as members read it: its data carries the cloud's owner and members. */
    private fun TripDoc.withAccess(): TripDoc {
        val data = item.data as? ItemData.TripData ?: return this
        return copy(item = item.copy(data = data.copy(ownerId = ownerId, memberIds = memberIds)))
    }

    private fun visibleTo(uid: String): List<SyncItem> {
        val myTrips = trips.filterValues { uid in it.memberIds }
        return myTrips.values.map { it.item } +
            shared.values.filter { it.tripUuid in myTrips } +
            personal[uid].orEmpty().values.filter { it.tripUuid in myTrips }
    }

    private fun keyOf(item: SyncItem) = if (item.kind == SyncKind.BUDGET) "budget:${item.uuid}" else item.uuid

    private fun check() {
        if (offline) throw CloudUnavailableException()
    }
}
