package com.example.planyourtrip.data.trip

import java.time.LocalDate
import java.time.LocalTime

/**
 * Domain model of one planned activity of a trip (named TripActivity so it never clashes with
 * android.app.Activity). Optional texts are null when not entered; the cost is in whole agorot.
 */
data class TripActivity(
    val id: Long,
    val tripId: Long,
    val date: LocalDate,
    /** Null = no time set; such activities are listed after the timed ones. */
    val startTime: LocalTime?,
    val title: String,
    val type: ActivityType,
    val note: String? = null,
    val location: String? = null,
    val estimatedCostAgorot: Long? = null,
    val booked: Boolean = false,
)

/** What the activity form saves (new or edited activity). Texts are trimmed by the repository. */
data class ActivityInput(
    val date: LocalDate,
    val startTime: LocalTime?,
    val title: String,
    val type: ActivityType,
    val note: String?,
    val location: String?,
    val estimatedCostAgorot: Long?,
    val booked: Boolean,
)
