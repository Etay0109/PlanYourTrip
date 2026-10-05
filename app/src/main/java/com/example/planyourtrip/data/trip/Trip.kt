package com.example.planyourtrip.data.trip

import java.time.LocalDate

/** Domain model used by the UI. Independent of Room so the storage can change later. */
data class Trip(
    val id: Long,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val color: TripColor,
    /** Total budget in agorot (1/100 shekel), or null when no budget is set yet. */
    val budgetAgorot: Long? = null,
    /** ISO 4217 code of the destination currency ("EUR"), or null when none is chosen yet. */
    val currencyCode: String? = null,
)
