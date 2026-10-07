package com.example.planyourtrip

import com.example.planyourtrip.data.trip.ChecklistItem
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.ChecklistRepository
import com.example.planyourtrip.data.trip.DeletedChecklistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * In-memory [ChecklistRepository]. Mirrors RoomChecklistRepository: only the owner of the item's trip (looked up in
 * [trips]) sees and changes it, texts are trimmed, packing items never get a due date, and [createdAt] grows with
 * every added item (the list order).
 */
class FakeChecklistRepository(
    private val trips: FakeTripRepository,
    initial: List<ChecklistItem> = emptyList(),
) : ChecklistRepository {
    val items = MutableStateFlow(initial)

    /** Number of [addItems] calls (suggestions), to check that a double tap adds once. */
    var addItemsCalls = 0
        private set

    private var clock = (initial.maxOfOrNull { it.createdAt } ?: 0) + 1

    private fun owns(ownerId: String, tripId: Long) =
        trips.trips.value.any { (owner, trip) -> owner == ownerId && trip.id == tripId }

    private fun nextId() = (items.value.maxOfOrNull { it.id } ?: 0) + 1

    override fun observeItems(tripId: Long, ownerId: String): Flow<List<ChecklistItem>> =
        combine(items, trips.trips) { list, _ ->
            if (!owns(ownerId, tripId)) emptyList() else list.filter { it.tripId == tripId }
        }

    override suspend fun addItem(tripId: Long, ownerId: String, kind: ChecklistKind, text: String): Long? {
        if (text.isBlank() || !owns(ownerId, tripId)) return null
        val id = nextId()
        items.update { it + ChecklistItem(id, tripId, kind, text.trim(), createdAt = clock++) }
        return id
    }

    override suspend fun addItems(tripId: Long, ownerId: String, kind: ChecklistKind, texts: List<String>) {
        addItemsCalls++
        texts.forEach { addItem(tripId, ownerId, kind, it) }
    }

    override suspend fun setChecked(id: Long, ownerId: String, checked: Boolean) = change(id, ownerId) {
        it.copy(checked = checked)
    }

    override suspend fun updateText(id: Long, ownerId: String, text: String) = change(id, ownerId) {
        if (text.isBlank()) it else it.copy(text = text.trim())
    }

    override suspend fun setDueDate(id: Long, ownerId: String, dueDate: LocalDate?) = change(id, ownerId) {
        if (it.kind == ChecklistKind.BEFORE_TRIP) it.copy(dueDate = dueDate) else it
    }

    override suspend fun deleteItem(id: Long, ownerId: String): DeletedChecklistItem? {
        val item = items.value.find { it.id == id && owns(ownerId, it.tripId) } ?: return null
        items.update { it - item }
        return DeletedChecklistItem(item)
    }

    override suspend fun restoreItem(deleted: DeletedChecklistItem, ownerId: String): Long? {
        if (!owns(ownerId, deleted.item.tripId)) return null
        val id = nextId()
        items.update { it + deleted.item.copy(id = id) }
        return id
    }

    private fun change(id: Long, ownerId: String, transform: (ChecklistItem) -> ChecklistItem) = items.update { list ->
        list.map { if (it.id == id && owns(ownerId, it.tripId)) transform(it) else it }
    }
}
