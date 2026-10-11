package com.example.planyourtrip.data.trip

import java.time.LocalDate

/** Domain model used by the UI. Independent of Room so the storage can change later. */
data class Trip(
    val id: Long,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val color: TripColor,
    /** My own budget for the trip in agorot (1/100 shekel; personal), or null when no budget is set yet. */
    val budgetAgorot: Long? = null,
    /** ISO 4217 code of the destination currency ("EUR"), or null when none is chosen yet. */
    val currencyCode: String? = null,
    /** Uid of the trip's owner (who created it). */
    val ownerId: String = "",
    /** Uids of the trip's members, owner first, then in join order (the same order on every device). */
    val memberIds: List<String> = emptyList(),
    /** The trip's stable id, the same on every device and in the cloud (invites and leaving use it). */
    val uuid: String = "",
    /** ISO 3166 code of the destination's country ("GB"), when it was picked from the suggestions; else null. */
    val countryCode: String? = null,
    /** The picked place's coordinates (both or neither); null for free text. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)
