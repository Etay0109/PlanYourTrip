package com.example.planyourtrip.ui.tripdetails.activity

import com.example.planyourtrip.data.trip.ActivityType
import java.time.LocalDate
import java.time.LocalTime

data class ActivityFormUiState(
    /** True when changing an existing activity (shows "Edit activity" and the delete action). */
    val isEditing: Boolean = false,
    /** True until the trip (and, when editing, the activity) is loaded; the fields stay hidden until then. */
    val isLoading: Boolean = true,
    val title: String = "",
    val type: ActivityType = ActivityType.ACTIVITY,
    /** Null only while loading. */
    val date: LocalDate? = null,
    /** Null = no start time. */
    val startTime: LocalTime? = null,
    val location: String = "",
    /** Estimated cost in shekels as typed, e.g. "25.50"; empty = no estimate. Parsed to agorot on save. */
    val cost: String = "",
    val booked: Boolean = false,
    val note: String = "",
    /** Trip dates: the date picker only offers days in this range. */
    val tripStart: LocalDate? = null,
    val tripEnd: LocalDate? = null,
    val titleError: ActivityFieldError? = null,
    val costError: ActivityFieldError? = null,
    val isSaving: Boolean = false,
    /** Set once the activity is saved or deleted (or the trip is gone); the screen then navigates back. */
    val done: Boolean = false,
)
