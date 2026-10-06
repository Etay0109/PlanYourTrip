package com.example.planyourtrip

import com.example.planyourtrip.data.sync.CloudStore
import com.example.planyourtrip.data.sync.CloudUnavailableException
import com.example.planyourtrip.data.sync.SyncItem
import com.example.planyourtrip.data.sync.SyncKind

/**
 * In-memory [CloudStore] (one map of documents per account), never Firestore. Set [offline] to fail every call,
 * or [failAfterWrites] to let that many more write calls succeed and fail the rest (offline in the middle).
 */
class FakeCloudStore : CloudStore {
    /** uid → uuid → document. */
    val docs = mutableMapOf<String, MutableMap<String, SyncItem>>()

    var offline = false
    var failAfterWrites: Int? = null

    /** Runs inside each successful write, before it returns (e.g. to make a local edit "during" the sync). */
    var onWrite: suspend () -> Unit = {}

    val calls = mutableListOf<String>()

    fun docsOf(uid: String): Map<String, SyncItem> = docs[uid].orEmpty()

    fun put(uid: String, vararg items: SyncItem) {
        items.forEach { docs.getOrPut(uid) { mutableMapOf() }[it.uuid] = it }
    }

    override suspend fun fetchAll(uid: String): List<SyncItem> {
        calls += "fetchAll:$uid"
        check()
        return docsOf(uid).values.toList()
    }

    override suspend fun fetch(uid: String, items: List<SyncItem>): List<SyncItem> {
        calls += "fetch:$uid:${items.size}"
        check()
        return items.mapNotNull { docsOf(uid)[it.uuid] }
    }

    override suspend fun write(uid: String, items: List<SyncItem>) {
        calls += "write:$uid:${items.size}"
        check()
        failAfterWrites?.let { left ->
            if (left <= 0) throw CloudUnavailableException()
            failAfterWrites = left - 1
        }
        val mine = docs.getOrPut(uid) { mutableMapOf() }
        for (item in items) {
            mine[item.uuid] = item
            if (item.kind == SyncKind.TRIP && item.isDeleted) {
                // Like Firestore: a trip tombstone turns its children into tombstones.
                mine.values.filter { it.tripUuid == item.uuid && !it.isDeleted }
                    .forEach { mine[it.uuid] = it.copy(updatedAt = item.updatedAt, data = null) }
            }
        }
        onWrite()
    }

    private fun check() {
        if (offline) throw CloudUnavailableException()
    }
}
