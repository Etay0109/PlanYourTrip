package com.example.planyourtrip.data.trip

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

/**
 * This device's copy of a trip for one signed-in account ([ownerId]). A shared trip has one row per account that
 * uses this device (unique `owner_id, uuid`); its cloud owner and members are [cloudOwnerId] / [memberIds].
 */
@Entity(tableName = "trips", indices = [Index("owner_id"), Index(value = ["owner_id", "uuid"], unique = true)])
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * Auth uid of the signed-in account this local copy belongs to (the column name predates shared trips). Every
     * query filters by it.
     */
    @ColumnInfo(name = "owner_id", defaultValue = "") val ownerId: String,
    val destination: String,
    /** Stored as epoch day (see [Converters]). */
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "end_date") val endDate: LocalDate,
    /** Stored as the enum name (see [Converters]). */
    val color: TripColor,
    /**
     * [ownerId]'s own budget for the trip in whole agorot; null = no budget yet. Personal: synced to
     * `memberData/{uid}` with its own [budgetUpdatedAt] / [budgetPending], never to the shared trip document.
     */
    @ColumnInfo(name = "budget_agorot") val budgetAgorot: Long? = null,
    /** 3-letter destination currency code; null = not chosen yet. */
    @ColumnInfo(name = "currency_code") val currencyCode: String? = null,
    /** Stable id shared by every device and Firestore (the document id); the Long [id] is local only. */
    @ColumnInfo(defaultValue = "") val uuid: String = UUID.randomUUID().toString(),
    /** Epoch millis of the last change on any device; the newer change wins when syncing. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
    /** True while this version still has to be uploaded to the cloud. */
    @ColumnInfo(name = "pending_sync", defaultValue = "1") val pendingSync: Boolean = true,
    /** Uid of the account that created the trip (its owner in the cloud: the only one who can delete it). */
    @ColumnInfo(name = "cloud_owner_id", defaultValue = "") val cloudOwnerId: String = ownerId,
    /**
     * Uids of the trip's members in the cloud's order (owner first, then join order), joined by "," (see
     * [Converters]). Also the first part of the travelers' list order (see `travelerOrder`).
     */
    @ColumnInfo(name = "member_ids", defaultValue = "") val memberIds: List<String> = listOf(ownerId),
    /** Epoch millis of the last change of [budgetAgorot]; 0 = never set on any device. */
    @ColumnInfo(name = "budget_updated_at", defaultValue = "0") val budgetUpdatedAt: Long = 0,
    /** True while the budget still has to be uploaded. */
    @ColumnInfo(name = "budget_pending", defaultValue = "0") val budgetPending: Boolean = false,
    /** The destination's country code ("GB") when it was picked from the suggestions; null = free text. */
    @ColumnInfo(name = "country_code") val countryCode: String? = null,
    /** The picked place's coordinates, both or neither (see [TripPlace]). */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

fun TripEntity.toTrip() = Trip(
    id = id,
    destination = destination,
    startDate = startDate,
    endDate = endDate,
    color = color,
    budgetAgorot = budgetAgorot,
    currencyCode = currencyCode,
    ownerId = cloudOwnerId,
    memberIds = memberIds,
    uuid = uuid,
    countryCode = countryCode,
    latitude = latitude,
    longitude = longitude,
)
