package com.example.planyourtrip.data.trip

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomActivityRepository(
    private val activityDao: ActivityDao,
    private val tripDao: TripDao,
) : ActivityRepository {

    override fun observeActivities(tripId: Long, ownerId: String): Flow<List<TripActivity>> =
        activityDao.observeForTrip(tripId, ownerId).map { list -> list.map { it.toTripActivity() } }

    override fun observeActivity(id: Long, ownerId: String): Flow<TripActivity?> =
        activityDao.observeById(id, ownerId).map { it?.toTripActivity() }

    override suspend fun addActivity(tripId: Long, ownerId: String, input: ActivityInput): Long? {
        // Never attach an activity to another account's trip.
        if (!tripDao.exists(tripId, ownerId)) return null
        val clean = input.normalized()
        return activityDao.insert(
            ActivityEntity(
                tripId = tripId,
                date = clean.date,
                startTime = clean.startTime,
                title = clean.title,
                note = clean.note,
                type = clean.type,
                location = clean.location,
                estimatedCostAgorot = clean.estimatedCostAgorot,
                booked = clean.booked,
            )
        )
    }

    override suspend fun updateActivity(id: Long, ownerId: String, input: ActivityInput) {
        val clean = input.normalized()
        activityDao.update(
            id = id,
            ownerId = ownerId,
            date = clean.date,
            startTime = clean.startTime,
            title = clean.title,
            note = clean.note,
            type = clean.type,
            location = clean.location,
            estimatedCostAgorot = clean.estimatedCostAgorot,
            booked = clean.booked,
        )
    }

    override suspend fun deleteActivity(id: Long, ownerId: String) = activityDao.delete(id, ownerId)
}
