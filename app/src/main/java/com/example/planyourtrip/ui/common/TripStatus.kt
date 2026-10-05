package com.example.planyourtrip.ui.common

import com.example.planyourtrip.data.trip.Trip
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Countdown shown for a trip that has not ended yet. */
sealed interface TripStatus {
    /** The trip is happening today. */
    data object Now : TripStatus

    /** The trip starts in [days] days (always ≥ 1). */
    data class InDays(val days: Int) : TripStatus
}

/** [TripStatus.Now] during the trip, [TripStatus.InDays] before it, null once it has ended. */
fun tripStatus(trip: Trip, today: LocalDate): TripStatus? = when {
    trip.endDate.isBefore(today) -> null
    !trip.startDate.isAfter(today) -> TripStatus.Now
    else -> TripStatus.InDays(ChronoUnit.DAYS.between(today, trip.startDate).toInt())
}
