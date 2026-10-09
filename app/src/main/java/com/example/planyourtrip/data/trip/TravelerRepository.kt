package com.example.planyourtrip.data.trip

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The travelers of the owner's trips (name only; trip members are not travelers, see [LEGACY_ME_KEY]). Names are
 * validated by the caller (non-blank, unique within the trip); the repository trims them.
 */
interface TravelerRepository {
    /** In the order they were added. Empty when the trip isn't the owner's. */
    fun observeTravelers(tripId: Long, ownerId: String): Flow<List<Traveler>>

    /** Adds a traveler with the next palette color. Null when the trip isn't the owner's or the name is blank. */
    suspend fun addTraveler(tripId: Long, ownerId: String, name: String): Long?

    suspend fun renameTraveler(id: Long, ownerId: String, name: String)

    /** Removes the traveler unless an expense or a settlement uses them. */
    suspend fun removeTraveler(id: Long, ownerId: String): RemoveTravelerResult
}

/** Payments between travelers. They change the balances only, never the budget. */
interface SettlementRepository {
    /** Newest first. Empty when the trip isn't the owner's. */
    fun observeSettlements(tripId: Long, ownerId: String): Flow<List<Settlement>>

    /** Null when the trip isn't the owner's, the amount isn't positive or both keys are the same. */
    suspend fun addSettlement(
        tripId: Long,
        ownerId: String,
        fromKey: String,
        toKey: String,
        amountAgorot: Long,
        date: LocalDate,
    ): Long?

    suspend fun deleteSettlement(id: Long, ownerId: String)
}
