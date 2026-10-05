package com.example.planyourtrip.data.trip

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Trips of one account at a time; callers pass the signed-in user's uid as [ownerId]. */
interface TripRepository {
    /** The owner's trips ordered by start date (earliest first). */
    fun observeTrips(ownerId: String): Flow<List<Trip>>

    /** One of the owner's trips, or null if it does not exist or belongs to someone else. */
    fun observeTrip(id: Long, ownerId: String): Flow<Trip?>

    /**
     * Creates a trip for [ownerId], picking its color automatically. [currencyCode] is what the user chose
     * (null = none); it is never guessed from the destination. Returns the new id.
     */
    suspend fun addTrip(
        ownerId: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        currencyCode: String? = null,
    ): Long

    /** Changes destination, dates and currency of one of the owner's trips; color and budget are kept. */
    suspend fun updateTrip(
        id: Long,
        ownerId: String,
        destination: String,
        startDate: LocalDate,
        endDate: LocalDate,
        currencyCode: String?,
    )

    /** Sets (or clears, with null) the total budget of one of the owner's trips, in agorot. */
    suspend fun setBudget(id: Long, ownerId: String, budgetAgorot: Long?)

    /** Sets (or clears, with null) the destination currency of one of the owner's trips (3-letter code). */
    suspend fun setCurrency(id: Long, ownerId: String, currencyCode: String?)
}
