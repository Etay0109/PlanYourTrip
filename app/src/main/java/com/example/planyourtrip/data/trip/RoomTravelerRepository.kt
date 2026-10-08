package com.example.planyourtrip.data.trip

import com.example.planyourtrip.data.sync.LocalChangeListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

/** Every change is stamped with [clock], marked pending (deletes are recorded) and reported to [changes]. */
class RoomTravelerRepository(
    private val travelerDao: TravelerDao,
    private val tripDao: TripDao,
    private val clock: Clock = Clock.systemUTC(),
    private val changes: LocalChangeListener = LocalChangeListener.None,
) : TravelerRepository {

    override fun observeTravelers(tripId: Long, ownerId: String): Flow<List<Traveler>> =
        travelerDao.observeForTrip(tripId, ownerId).map { list -> list.map { it.toTraveler() } }

    override suspend fun addTraveler(tripId: Long, ownerId: String, name: String): Long? {
        val clean = name.trim()
        if (clean.isEmpty() || !tripDao.exists(tripId, ownerId)) return null
        val now = clock.millis()
        val count = travelerDao.count(tripId, ownerId)
        // Always after the trip's last traveler, so the list order (and who gets the extra agorot) is stable.
        val createdAt = travelerDao.maxCreatedAt(tripId)?.let { maxOf(now, it + 1) } ?: now
        return travelerDao.insert(
            TravelerEntity(
                tripId = tripId,
                name = clean,
                color = TravelerColor.forIndex(count),
                createdAt = createdAt,
                updatedAt = now,
            )
        ).also { changes.onLocalChange() }
    }

    override suspend fun renameTraveler(id: Long, ownerId: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        travelerDao.rename(id, ownerId, clean, clock.millis())
        changes.onLocalChange()
    }

    override suspend fun removeTraveler(id: Long, ownerId: String): RemoveTravelerResult =
        travelerDao.removeIfUnused(id, ownerId, clock.millis()).also {
            if (it == RemoveTravelerResult.REMOVED) changes.onLocalChange()
        }
}
