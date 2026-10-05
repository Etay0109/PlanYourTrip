package com.example.planyourtrip

import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.data.trip.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** In-memory [TripRepository] for unit tests. Mirrors RoomTripRepository, including per-owner filtering. */
class FakeTripRepository(initial: List<Pair<String, Trip>> = emptyList()) : TripRepository {
    /** (ownerId, trip) pairs. */
    val trips = MutableStateFlow(initial)

    fun tripsOf(ownerId: String): List<Trip> = trips.value.filter { it.first == ownerId }.map { it.second }

    override fun observeTrips(ownerId: String): Flow<List<Trip>> =
        trips.map { list -> list.filter { it.first == ownerId }.map { it.second }.sortedBy { it.startDate } }

    override fun observeTrip(id: Long, ownerId: String): Flow<Trip?> =
        trips.map { list -> list.find { it.first == ownerId && it.second.id == id }?.second }

    override suspend fun addTrip(
        ownerId: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        currencyCode: String?,
    ): Long {
        val id = (trips.value.maxOfOrNull { it.second.id } ?: 0) + 1
        trips.update { list ->
            val color = TripColor.forIndex(list.count { it.first == ownerId })
            list + (ownerId to Trip(id, destination.trim(), startDate, endDate, color, currencyCode = currencyCode))
        }
        return id
    }

    override suspend fun updateTrip(
        id: Long,
        ownerId: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        currencyCode: String?,
    ) = updateOwned(id, ownerId) {
        it.copy(destination = destination.trim(), startDate = startDate, endDate = endDate, currencyCode = currencyCode)
    }

    override suspend fun setBudget(id: Long, ownerId: String, budgetAgorot: Long?) =
        updateOwned(id, ownerId) { it.copy(budgetAgorot = budgetAgorot) }

    override suspend fun setCurrency(id: Long, ownerId: String, currencyCode: String?) =
        updateOwned(id, ownerId) { it.copy(currencyCode = currencyCode) }

    private fun updateOwned(id: Long, ownerId: String, change: (Trip) -> Trip) = trips.update { list ->
        list.map { (owner, trip) -> owner to if (owner == ownerId && trip.id == id) change(trip) else trip }
    }
}
