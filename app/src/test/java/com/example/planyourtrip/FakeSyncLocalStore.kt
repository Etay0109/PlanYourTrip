package com.example.planyourtrip

import com.example.planyourtrip.data.sync.LocalItem
import com.example.planyourtrip.data.sync.MergePlan
import com.example.planyourtrip.data.sync.SyncItem
import com.example.planyourtrip.data.sync.SyncKind
import com.example.planyourtrip.data.sync.SyncLocalStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [SyncLocalStore] with the same rules as RoomSyncLocalStore (owner scoping, updatedAt guards, cascade). */
class FakeSyncLocalStore : SyncLocalStore {
    data class Row(val ownerId: String, val item: SyncItem, val pending: Boolean)

    /** uuid → live row or tombstone (a tombstone row is always pending). */
    val rows = MutableStateFlow<Map<String, Row>>(emptyMap())

    fun rowsOf(ownerId: String): List<Row> = rows.value.values.filter { it.ownerId == ownerId }
    fun liveOf(ownerId: String): Map<String, SyncItem> =
        rowsOf(ownerId).filter { !it.item.isDeleted }.associate { it.item.uuid to it.item }

    /** A change made on this device (create or edit): pending until uploaded. */
    fun edit(ownerId: String, item: SyncItem, pending: Boolean = true) =
        rows.update { it + (item.uuid to Row(ownerId, item, pending)) }

    /** A delete made on this device: removes the item (and a trip's children) and records a tombstone. */
    fun delete(ownerId: String, item: SyncItem, at: Long) = rows.update { current ->
        current.filterValues { it.item.tripUuid != item.uuid || item.kind != SyncKind.TRIP } +
            (item.uuid to Row(ownerId, item.copy(updatedAt = at, data = null), pending = true))
    }

    override suspend fun snapshot(ownerId: String, pendingOnly: Boolean): List<LocalItem> =
        rowsOf(ownerId).filter { !pendingOnly || it.pending }.map { LocalItem(it.item, it.pending) }

    override suspend fun apply(ownerId: String, plan: MergePlan) {
        plan.saveLocally.forEach { save(ownerId, it) }
        plan.deleteLocally.forEach { item ->
            val row = rows.value[item.uuid]
            if (row != null && row.ownerId == ownerId && !row.item.isDeleted && row.item.updatedAt == item.updatedAt) {
                rows.update { current ->
                    (current - item.uuid).filterValues { item.kind != SyncKind.TRIP || it.item.tripUuid != item.uuid }
                }
            }
        }
        plan.dropTombstones.forEach { dropTombstone(ownerId, it) }
    }

    private fun save(ownerId: String, item: SyncItem) {
        val existing = rows.value[item.uuid]
        if (existing != null) {
            if (existing.ownerId != ownerId || existing.item.updatedAt > item.updatedAt) return
        }
        if (item.kind != SyncKind.TRIP) {
            val trip = rows.value[item.tripUuid] ?: return
            if (trip.ownerId != ownerId || trip.item.isDeleted) return
        }
        rows.update { it + (item.uuid to Row(ownerId, item, pending = false)) }
    }

    override suspend fun markSynced(ownerId: String, item: SyncItem) {
        val row = rows.value[item.uuid] ?: return
        if (row.ownerId != ownerId || row.item.updatedAt != item.updatedAt || row.item.isDeleted != item.isDeleted) return
        if (item.isDeleted) dropTombstone(ownerId, item)
        else rows.update { it + (item.uuid to row.copy(pending = false)) }
    }

    private fun dropTombstone(ownerId: String, item: SyncItem) {
        val row = rows.value[item.uuid] ?: return
        if (row.ownerId == ownerId && row.item.isDeleted && row.item.updatedAt == item.updatedAt) {
            rows.update { it - item.uuid }
        }
    }

    override fun pendingCount(ownerId: String): Flow<Int> =
        rows.map { all -> all.values.count { it.ownerId == ownerId && it.pending } }

    override suspend fun deleteAllFor(ownerId: String) = rows.update { all -> all.filterValues { it.ownerId != ownerId } }
}
