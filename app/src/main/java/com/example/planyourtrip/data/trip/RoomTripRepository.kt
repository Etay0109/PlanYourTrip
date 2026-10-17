package com.example.planyourtrip.data.trip

import com.example.planyourtrip.data.sync.LocalChangeListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/**
 * Room is the only source for the screens. Every change is stamped with [clock] and marked pending, then
 * [changes] tells the sync layer to upload it.
 */
class RoomTripRepository(
    private val dao: TripDao,
    private val clock: Clock = Clock.systemUTC(),
    private val changes: LocalChangeListener = LocalChangeListener.None,
) : TripRepository {

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
        place: TripPlace,
    ): Long {
        // Each account's palette starts at the first color.
        val color = TripColor.forIndex(dao.count(ownerId))
        val where = place.normalized()
        return dao.insert(
            TripEntity(
                ownerId = ownerId,
                destination = destination.trim().limitTo(TextLimits.DESTINATION),
                startDate = startDate,
                endDate = endDate,
                color = color,
                currencyCode = currencyCode,
                updatedAt = clock.millis(),
                countryCode = where.countryCode,
                latitude = where.latitude,
                longitude = where.longitude,
            )
        ).also { changes.onLocalChange() }
    }

    override suspend fun updateTrip(
        id: Long,
        ownerId: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        currencyCode: String?,
        place: TripPlace,
    ) {
        val where = place.normalized()
        dao.update(
            id, ownerId, destination.trim().limitTo(TextLimits.DESTINATION), startDate, endDate, currencyCode,
            where.countryCode, where.latitude, where.longitude, clock.millis(),
        )
        changes.onLocalChange()
    }

    override suspend fun setBudget(id: Long, ownerId: String, budgetAgorot: Long?) {
        dao.setBudget(id, ownerId, budgetAgorot, clock.millis())
        changes.onLocalChange()
    }

    override suspend fun setCurrency(id: Long, ownerId: String, currencyCode: String?) {
        dao.setCurrency(id, ownerId, currencyCode, clock.millis())
        changes.onLocalChange()
    }

    override suspend fun deleteTrip(id: Long, ownerId: String): String? =
        dao.deleteAndRecord(id, ownerId, clock.millis())?.also { changes.onLocalChange() }
}
