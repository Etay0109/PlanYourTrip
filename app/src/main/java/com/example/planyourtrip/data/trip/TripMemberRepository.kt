package com.example.planyourtrip.data.trip

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** The member documents of a trip as of the last full sync (names of members, also of those who left). */
interface TripMemberRepository {
    fun observeMembers(tripId: Long, ownerId: String): Flow<List<TripMember>>

    companion object {
        /** No member documents (a trip nobody joined); for previews and tests that don't need members. */
        val None = object : TripMemberRepository {
            override fun observeMembers(tripId: Long, ownerId: String): Flow<List<TripMember>> = flowOf(emptyList())
        }
    }
}

class RoomTripMemberRepository(private val dao: TripMemberDao) : TripMemberRepository {
    override fun observeMembers(tripId: Long, ownerId: String): Flow<List<TripMember>> =
        dao.observeForTrip(tripId, ownerId).map { list -> list.map { it.toTripMember() } }
}
