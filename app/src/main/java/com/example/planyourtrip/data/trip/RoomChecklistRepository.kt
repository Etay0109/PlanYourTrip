package com.example.planyourtrip.data.trip

import com.example.planyourtrip.data.sync.LocalChangeListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** Every change is stamped with [clock], marked pending (deletes are recorded) and reported to [changes]. */
class RoomChecklistRepository(
    private val checklistDao: ChecklistDao,
    private val tripDao: TripDao,
    private val clock: Clock = Clock.systemUTC(),
    private val changes: LocalChangeListener = LocalChangeListener.None,
) : ChecklistRepository {

    override fun observeItems(tripId: Long, ownerId: String): Flow<List<ChecklistItem>> =
        checklistDao.observeForTrip(tripId, ownerId).map { list -> list.map { it.toChecklistItem() } }

    override suspend fun addItem(tripId: Long, ownerId: String, kind: ChecklistKind, text: String): Long? {
        val clean = text.trim()
        if (clean.isEmpty() || !tripDao.exists(tripId, ownerId)) return null
        val now = clock.millis()
        return checklistDao.insert(newItem(tripId, kind, clean, createdAt = now, updatedAt = now))
            .also { changes.onLocalChange() }
    }

    override suspend fun addItems(tripId: Long, ownerId: String, kind: ChecklistKind, texts: List<String>) {
        val clean = texts.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty() || !tripDao.exists(tripId, ownerId)) return
        val now = clock.millis()
        // One millisecond apart, so every device lists them in this order.
        checklistDao.insertAll(clean.mapIndexed { i, text -> newItem(tripId, kind, text, now + i, now) })
        changes.onLocalChange()
    }

    override suspend fun setChecked(id: Long, ownerId: String, checked: Boolean) {
        checklistDao.setChecked(id, ownerId, checked, clock.millis())
        changes.onLocalChange()
    }

    override suspend fun updateText(id: Long, ownerId: String, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        checklistDao.setText(id, ownerId, clean, clock.millis())
        changes.onLocalChange()
    }

    override suspend fun setDueDate(id: Long, ownerId: String, dueDate: LocalDate?) {
        checklistDao.setDueDate(id, ownerId, dueDate, clock.millis())
        changes.onLocalChange()
    }

    override suspend fun deleteItem(id: Long, ownerId: String): DeletedChecklistItem? {
        val item = checklistDao.byId(id, ownerId)?.toChecklistItem() ?: return null
        checklistDao.deleteAndRecord(id, ownerId, clock.millis())
        changes.onLocalChange()
        return DeletedChecklistItem(item)
    }

    override suspend fun restoreItem(deleted: DeletedChecklistItem, ownerId: String): Long? {
        val item = deleted.item
        if (!tripDao.exists(item.tripId, ownerId)) return null
        // A new row with a new uuid (the recorded delete still uploads); the old createdAt keeps its place.
        return checklistDao.insert(
            ChecklistItemEntity(
                tripId = item.tripId,
                list = item.kind,
                text = item.text,
                checked = item.checked,
                dueDate = item.dueDate.takeIf { item.kind == ChecklistKind.BEFORE_TRIP },
                createdAt = item.createdAt,
                updatedAt = clock.millis(),
            )
        ).also { changes.onLocalChange() }
    }

    private fun newItem(tripId: Long, kind: ChecklistKind, text: String, createdAt: Long, updatedAt: Long) =
        ChecklistItemEntity(
            tripId = tripId,
            list = kind,
            text = text,
            checked = false,
            dueDate = null,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
