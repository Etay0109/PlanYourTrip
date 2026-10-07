package com.example.planyourtrip.data.trip

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What deleting an activity removed, so "Undo" can put it back: the activity as it was. */
data class DeletedActivity(val activity: TripActivity)

/**
 * The last deleted activity, handed from the activity form (which closes) to the trip screen (which shows "Activity
 * deleted" with "Undo"). App-scoped and in memory only; a newer delete replaces an older one.
 */
class DeletedActivityStore {
    private val _last = MutableStateFlow<DeletedActivity?>(null)
    val last: StateFlow<DeletedActivity?> = _last.asStateFlow()

    fun offer(deleted: DeletedActivity) {
        _last.value = deleted
    }

    /** Takes [deleted] out of the store (undone, or its message was dismissed); a newer delete is kept. */
    fun clear(deleted: DeletedActivity) {
        _last.compareAndSet(deleted, null)
    }
}
