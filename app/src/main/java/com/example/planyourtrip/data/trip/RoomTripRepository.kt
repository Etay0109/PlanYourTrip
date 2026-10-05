package com.example.planyourtrip.data.trip

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomTripRepository(private val dao: TripDao) : TripRepository {

    override fun observeTrips(ownerId: String): Flow<List<Trip>> =
        dao.observeAll(ownerId).map { list -> list.map { it.toTrip() } }

    override fun observeTrip(id: Long, ownerId: String): Flow<Trip?> =
        dao.observeById(id, ownerId).map { it?.toTrip() }

    override suspend fun addTrip(
        ownerId: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        currencyCode: String?,
    ): Long {
        // Each account's palette starts at the first color.
        val color = TripColor.forIndex(dao.count(ownerId))
        return dao.insert(
            TripEntity(
                ownerId = ownerId,
                destination = destination.trim(),
                startDate = startDate,
                endDate = endDate,
                color = color,
                currencyCode = currencyCode,
            )
        )
    }

    override suspend fun updateTrip(
        id: Long,
        ownerId: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        currencyCode: String?,
    ) = dao.update(id, ownerId, destination.trim(), startDate, endDate, currencyCode)

    override suspend fun setBudget(id: Long, ownerId: String, budgetAgorot: Long?) =
        dao.setBudget(id, ownerId, budgetAgorot)

    override suspend fun setCurrency(id: Long, ownerId: String, currencyCode: String?) =
        dao.setCurrency(id, ownerId, currencyCode)
}
