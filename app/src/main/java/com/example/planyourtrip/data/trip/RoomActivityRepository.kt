package com.example.planyourtrip.data.trip

import com.example.planyourtrip.data.sync.LocalChangeListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock

/** Every change is stamped with [clock], marked pending (deletes are recorded) and reported to [changes]. */
class RoomActivityRepository(
    private val activityDao: ActivityDao,
    private val tripDao: TripDao,
    private val clock: Clock = Clock.systemUTC(),
    private val changes: LocalChangeListener = LocalChangeListener.None,
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
                updatedAt = clock.millis(),
            )
        ).also { changes.onLocalChange() }
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
            updatedAt = clock.millis(),
        )
        changes.onLocalChange()
    }

    override suspend fun deleteActivity(id: Long, ownerId: String): DeletedActivity? {
        val activity = activityDao.observeById(id, ownerId).first()?.toTripActivity() ?: return null
        activityDao.deleteAndRecord(id, ownerId, clock.millis())
        changes.onLocalChange()
        return DeletedActivity(activity)
    }

    override suspend fun restoreActivity(deleted: DeletedActivity, ownerId: String): Long? {
        val activity = deleted.activity
        if (!tripDao.exists(activity.tripId, ownerId)) return null
        // A new row with a new uuid: the recorded delete still uploads, so no device mixes the old and new items.
        return activityDao.insert(
            ActivityEntity(
                tripId = activity.tripId,
                date = activity.date,
                startTime = activity.startTime,
                title = activity.title,
                note = activity.note,
                type = activity.type,
                location = activity.location,
                estimatedCostAgorot = activity.estimatedCostAgorot,
                booked = activity.booked,
                updatedAt = clock.millis(),
            )
        ).also { changes.onLocalChange() }
    }
}
