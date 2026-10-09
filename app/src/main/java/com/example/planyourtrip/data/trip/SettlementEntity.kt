package com.example.planyourtrip.data.trip

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/** A recorded payment between two travelers. Deleting the trip deletes its settlements (foreign key CASCADE). */
@Entity(
    tableName = "settlements",
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
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trip_id") val tripId: Long,
    /** Who paid: a member uid or a traveler uuid. */
    @ColumnInfo(name = "from_traveler") val fromKey: String,
    /** Who received the money: a member uid or a traveler uuid. */
    @ColumnInfo(name = "to_traveler") val toKey: String,
    /** Whole agorot, > 0. */
    @ColumnInfo(name = "amount_agorot") val amountAgorot: Long,
    /** Stored as epoch day (see [Converters]). */
    val date: LocalDate,
    /** Epoch millis of when it was recorded (history order, newest first). */
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val uuid: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
    @ColumnInfo(name = "pending_sync", defaultValue = "1") val pendingSync: Boolean = true,
)

fun SettlementEntity.toSettlement() = Settlement(
    id = id,
    tripId = tripId,
    uuid = uuid,
    fromKey = fromKey,
    toKey = toKey,
    amountAgorot = amountAgorot,
    date = date,
)
