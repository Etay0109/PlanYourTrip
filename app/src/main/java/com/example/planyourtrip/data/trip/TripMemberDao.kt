package com.example.planyourtrip.data.trip

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Read side of the trip members (the sync writes them through SyncDao). Scoped to the owner of the local copy. */
@Dao
interface TripMemberDao {
    @Query(
        "SELECT m.* FROM trip_members m JOIN trips t ON t.id = m.trip_id " +
            "WHERE m.trip_id = :tripId AND t.owner_id = :ownerId ORDER BY m.joined_at, m.id"
    )
    fun observeForTrip(tripId: Long, ownerId: String): Flow<List<TripMemberEntity>>
}
