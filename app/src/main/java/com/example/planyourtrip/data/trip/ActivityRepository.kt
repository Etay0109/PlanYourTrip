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

    suspend fun deleteActivity(id: Long, ownerId: String)
}

/** Trims the texts and turns blank optional texts into null, as stored. */
fun ActivityInput.normalized(): ActivityInput = copy(
    title = title.trim(),
    note = note?.trim()?.takeIf { it.isNotEmpty() },
    location = location?.trim()?.takeIf { it.isNotEmpty() },
)
