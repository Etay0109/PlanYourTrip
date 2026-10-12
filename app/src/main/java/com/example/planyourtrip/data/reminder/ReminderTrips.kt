package com.example.planyourtrip.data.reminder

import com.example.planyourtrip.data.trip.ChecklistDao
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripDao
import com.example.planyourtrip.data.trip.toTrip

/** What a reminder reads when it fires or is tapped: the trip by its uuid and its "Before the trip" list. */
interface ReminderTrips {
    /** One of [ownerId]'s trips, or null (deleted, left, another account). */
    suspend fun trip(tripUuid: String, ownerId: String): Trip?

    suspend fun beforeTripCount(tripUuid: String, ownerId: String): BeforeTripCount
}

class RoomReminderTrips(private val tripDao: TripDao, private val checklistDao: ChecklistDao) : ReminderTrips {
    override suspend fun trip(tripUuid: String, ownerId: String): Trip? = tripDao.byUuid(tripUuid, ownerId)?.toTrip()

    override suspend fun beforeTripCount(tripUuid: String, ownerId: String): BeforeTripCount =
        checklistDao.countBeforeTrip(tripUuid, ownerId)
}
