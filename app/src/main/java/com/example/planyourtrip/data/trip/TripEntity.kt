package com.example.planyourtrip.data.trip

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "trips", indices = [Index("owner_id"), Index(value = ["uuid"], unique = true)])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Auth uid of the account that owns the trip. Every query filters by it. */
    @ColumnInfo(name = "owner_id", defaultValue = "") val ownerId: String,
    val destination: String,
    /** Stored as epoch day (see [Converters]). */
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate,
    /** Stored as the enum name (see [Converters]). */
    val color: TripColor,
    /** Total budget in whole agorot; null = no budget yet. */
    @ColumnInfo(name = "budget_agorot") val budgetAgorot: Long? = null,
    /** 3-letter destination currency code; null = not chosen yet. */
    @ColumnInfo(name = "currency_code") val currencyCode: String? = null,
    /** Stable id shared by every device and Firestore (the document id); the Long [id] is local only. */
    @ColumnInfo(defaultValue = "") val uuid: String = UUID.randomUUID().toString(),
    /** Epoch millis of the last change on any device; the newer change wins when syncing. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
    /** True while this version still has to be uploaded to the cloud. */
    @ColumnInfo(name = "pending_sync", defaultValue = "1") val pendingSync: Boolean = true,
)

fun TripEntity.toTrip() = Trip(
    id = id,
    destination = destination,
    startDate = startDate,
    endDate = endDate,
    color = color,
    budgetAgorot = budgetAgorot,
    currencyCode = currencyCode,
)
