package com.example.planyourtrip.data.trip

import kotlinx.coroutines.flow.Flow

/** Planned activities of the owner's trips; callers pass the signed-in user's uid as [ownerId]. */
interface ActivityRepository {
    /** The trip's activities (any order). Empty if the trip is not the owner's. */
    fun observeActivities(tripId: Long, ownerId: String): Flow<List<TripActivity>>

    /** One activity, or null if it does not exist or belongs to someone else. */
    fun observeActivity(id: Long, ownerId: String): Flow<TripActivity?>

    /** Adds an activity to one of the owner's trips. Returns the new id, or null if the trip is not the owner's. */
    suspend fun addActivity(tripId: Long, ownerId: String, input: ActivityInput): Long?

    suspend fun updateActivity(id: Long, ownerId: String, input: ActivityInput)

    /** Deletes the activity. Returns what was deleted (for "Undo"), or null when there was no such activity of the owner. */
    suspend fun deleteActivity(id: Long, ownerId: String): DeletedActivity?

    /**
     * "Undo" of [deleteActivity]: adds the activity back as a new item. Returns the new activity id, or null when the
     * trip is gone or not the owner's.
     */
    suspend fun restoreActivity(deleted: DeletedActivity, ownerId: String): Long?
}

/** Trims the texts and turns blank optional texts into null, as stored. */
fun ActivityInput.normalized(): ActivityInput = copy(
    title = title.trim().limitTo(TextLimits.ACTIVITY_TITLE),
    note = note?.trim()?.takeIf { it.isNotEmpty() }?.limitTo(TextLimits.ACTIVITY_NOTE),
    location = location?.trim()?.takeIf { it.isNotEmpty() }?.limitTo(TextLimits.ACTIVITY_LOCATION),
)
