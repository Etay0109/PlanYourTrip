package com.example.planyourtrip.data.trip

import com.example.planyourtrip.data.sync.LocalChangeListener
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** Every change is stamped with [clock], marked pending (deletes are recorded) and reported to [changes]. */
class RoomSettlementRepository(
    private val settlementDao: SettlementDao,
    private val tripDao: TripDao,
    private val clock: Clock = Clock.systemUTC(),
    private val changes: LocalChangeListener = LocalChangeListener.None,
) : SettlementRepository {

    override fun observeSettlements(tripId: Long, ownerId: String): Flow<List<Settlement>> =
        settlementDao.observeForTrip(tripId, ownerId).map { list -> list.map { it.toSettlement() } }

    override suspend fun addSettlement(
        tripId: Long,
        ownerId: String,
        fromKey: String,
        toKey: String,
        amountAgorot: Long,
        date: LocalDate,
    ): Long? {
        if (amountAgorot <= 0 || fromKey == toKey || !tripDao.exists(tripId, ownerId)) return null
        val now = clock.millis()
        return settlementDao.insert(
            SettlementEntity(
                tripId = tripId,
                fromKey = fromKey,
                toKey = toKey,
                amountAgorot = amountAgorot,
                date = date,
                createdAt = now,
                updatedAt = now,
            )
        ).also { changes.onLocalChange() }
    }

    override suspend fun deleteSettlement(id: Long, ownerId: String) {
        settlementDao.deleteAndRecord(id, ownerId, clock.millis())
        changes.onLocalChange()
    }
}
