package com.example.planyourtrip.data.trip

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "trips", indices = [Index("owner_id")])
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
