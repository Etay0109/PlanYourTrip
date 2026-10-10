package com.example.planyourtrip.data.trip

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The cloud's member documents of a trip ([TripMember]), as of the last full sync. Never pending, never synced up:
 * the sync replaces a trip's rows as a whole. Deleting the trip deletes them (foreign key CASCADE).
 */
@Entity(
    tableName = "trip_members",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trip_id"), Index(value = ["trip_id", "uid"], unique = true)],
)
data class TripMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trip_id") val tripId: Long,
    val uid: String,
    val name: String,
    /** Epoch millis of when they joined, or null when unknown. */
    @ColumnInfo(name = "joined_at") val joinedAt: Long?,
    val left: Boolean,
)

fun TripMemberEntity.toTripMember() = TripMember(uid = uid, name = name, joinedAt = joinedAt, left = left)
