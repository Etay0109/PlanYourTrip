package com.example.planyourtrip

import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.ActivityRepository
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.data.trip.normalized
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * In-memory [ActivityRepository] for unit tests. Mirrors RoomActivityRepository: activities are visible and
 * changeable only by the owner of their trip (looked up in [trips]); texts are trimmed, blank ones become null.
 */
class FakeActivityRepository(
    private val trips: FakeTripRepository,
    initial: List<TripActivity> = emptyList(),
) : ActivityRepository {
    val activities = MutableStateFlow(initial)

    private fun owns(ownerId: String, tripId: Long) =
        trips.trips.value.any { (owner, trip) -> owner == ownerId && trip.id == tripId }

    override fun observeActivities(tripId: Long, ownerId: String): Flow<List<TripActivity>> =
        combine(activities, trips.trips) { list, _ ->
            if (!owns(ownerId, tripId)) emptyList() else list.filter { it.tripId == tripId }
        }

    override fun observeActivity(id: Long, ownerId: String): Flow<TripActivity?> =
        combine(activities, trips.trips) { list, _ -> list.find { it.id == id && owns(ownerId, it.tripId) } }

    override suspend fun addActivity(tripId: Long, ownerId: String, input: ActivityInput): Long? {
        if (!owns(ownerId, tripId)) return null
        val id = (activities.value.maxOfOrNull { it.id } ?: 0) + 1
        activities.update { it + input.toActivity(id, tripId) }
        return id
    }

    override suspend fun updateActivity(id: Long, ownerId: String, input: ActivityInput) = activities.update { list ->
        list.map { if (it.id == id && owns(ownerId, it.tripId)) input.toActivity(id, it.tripId) else it }
    }

    override suspend fun deleteActivity(id: Long, ownerId: String) =
        activities.update { list -> list.filterNot { it.id == id && owns(ownerId, it.tripId) } }

    private fun ActivityInput.toActivity(id: Long, tripId: Long): TripActivity = normalized().let {
        TripActivity(
            id = id,
            tripId = tripId,
            date = it.date,
            startTime = it.startTime,
            title = it.title,
            type = it.type,
            note = it.note,
            location = it.location,
            estimatedCostAgorot = it.estimatedCostAgorot,
            booked = it.booked,
        )
    }
}
