package com.example.planyourtrip.data.trip

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** The checklists of the owner's trips; callers pass the signed-in user's uid as [ownerId]. */
interface ChecklistRepository {
    /** The trip's items of both lists, in the order they were added. Empty if the trip is not the owner's. */
    fun observeItems(tripId: Long, ownerId: String): Flow<List<ChecklistItem>>

    /** Adds one unchecked item at the end of [kind]. Returns its id, or null when the trip is not the owner's. */
    suspend fun addItem(tripId: Long, ownerId: String, kind: ChecklistKind, text: String): Long?

    /** Adds several unchecked items at the end of [kind], in this order (the suggested items). */
    suspend fun addItems(tripId: Long, ownerId: String, kind: ChecklistKind, texts: List<String>)

    suspend fun setChecked(id: Long, ownerId: String, checked: Boolean)

    suspend fun updateText(id: Long, ownerId: String, text: String)

    /** Sets or clears ([dueDate] null) the due date; ignored for packing items. */
    suspend fun setDueDate(id: Long, ownerId: String, dueDate: LocalDate?)

    /** Deletes the item. Returns what was deleted (for "Undo"), or null when there was no such item of the owner. */
    suspend fun deleteItem(id: Long, ownerId: String): DeletedChecklistItem?

    /**
     * "Undo" of [deleteItem]: adds the item back as a new item (new uuid) at its old place in the list. Returns
     * the new id, or null when the trip is gone or not the owner's.
     */
    suspend fun restoreItem(deleted: DeletedChecklistItem, ownerId: String): Long?
}
