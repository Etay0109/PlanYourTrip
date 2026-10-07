package com.example.planyourtrip.data.trip

import java.time.LocalDate

/**
 * The two checklists of a trip (Prep tab). Stored by [name] in Room and Firestore, so entries can be reordered or
 * appended, but never renamed or removed without a migration (an unknown name falls back to [BEFORE_TRIP]).
 */
enum class ChecklistKind { BEFORE_TRIP, PACKING }

/**
 * Domain model of one checklist item. [dueDate] is only ever set on [ChecklistKind.BEFORE_TRIP] items.
 * [createdAt] (epoch millis, synced) keeps the items in the order they were added on every device.
 */
data class ChecklistItem(
    val id: Long,
    val tripId: Long,
    val kind: ChecklistKind,
    val text: String,
    val checked: Boolean = false,
    val dueDate: LocalDate? = null,
    val createdAt: Long = 0,
)

/** What deleting a checklist item removed, so "Undo" can put it back: the item as it was. */
data class DeletedChecklistItem(val item: ChecklistItem)
