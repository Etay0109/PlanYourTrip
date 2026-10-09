package com.example.planyourtrip.data.trip

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A name-only traveler of a trip (members are not rows, see [LEGACY_ME_KEY]). Deleting the trip deletes its travelers
 * (foreign key CASCADE).
 */
@Entity(
    tableName = "travelers",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trip_id"), Index(value = ["trip_id", "uuid"], unique = true)],
)
data class TravelerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trip_id") val tripId: Long,
    val name: String,
    /** Stored as the enum name (see [Converters]). */
    val color: TravelerColor,
    /** Epoch millis of when the traveler was added; travelers are listed by it (then id), after "You". */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /** Stable id shared by every device and Firestore; also the traveler's key in expenses and settlements. */
    val uuid: String = UUID.randomUUID().toString(),
    /** Epoch millis of the last change on any device; the newer change wins when syncing. */
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
    /** True while this version still has to be uploaded to the cloud. */
    @ColumnInfo(name = "pending_sync", defaultValue = "1") val pendingSync: Boolean = true,
)

fun TravelerEntity.toTraveler() = Traveler(id = id, tripId = tripId, uuid = uuid, name = name, color = color)
