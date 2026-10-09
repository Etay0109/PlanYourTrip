package com.example.planyourtrip.data.trip

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * A planned activity of a trip. Deleting the trip deletes its activities (foreign key CASCADE).
 */
@Entity(
    tableName = "activities",
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
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trip_id") val tripId: Long,
    /** Stored as epoch day (see [Converters]). */
    val date: LocalDate,
    /** Minute of the day (see [Converters]); null = no time. */
    @ColumnInfo(name = "start_time") val startTime: LocalTime?,
    val title: String,
    val note: String?,
    /** Stored as the enum name (see [Converters]). */
    val type: ActivityType,
    val location: String?,
    /** Whole agorot; null = no estimate. */
    @ColumnInfo(name = "estimated_cost_agorot") val estimatedCostAgorot: Long?,
    val booked: Boolean,
    /** Stable id shared by every device and Firestore (the document id); the Long [id] is local only. */
    @ColumnInfo(defaultValue = "") val uuid: String = UUID.randomUUID().toString(),
    /** Epoch millis of the last change on any device; the newer change wins when syncing. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
    /** True while this version still has to be uploaded to the cloud. */
    @ColumnInfo(name = "pending_sync", defaultValue = "1") val pendingSync: Boolean = true,
)

fun ActivityEntity.toTripActivity() = TripActivity(
    id = id,
    tripId = tripId,
    date = date,
    startTime = startTime,
    title = title,
    type = type,
    note = note,
    location = location,
    estimatedCostAgorot = estimatedCostAgorot,
    booked = booked,
)
