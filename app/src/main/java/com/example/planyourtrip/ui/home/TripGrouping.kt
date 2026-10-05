package com.example.planyourtrip.ui.home

import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.ui.common.TripStatus
import com.example.planyourtrip.ui.common.tripStatus
import java.time.LocalDate

data class ActiveTrip(val trip: Trip, val status: TripStatus)

data class TripSections(
    val next: ActiveTrip?,
    val upcoming: List<ActiveTrip>,
    val past: List<Trip>,
) {
    val isEmpty: Boolean get() = next == null && upcoming.isEmpty() && past.isEmpty()
}

/**
 * Splits trips into the three home sections relative to [today]:
 * - current (start ≤ today ≤ end) and future trips, earliest start first → the first one is
 *   the next trip, the rest are upcoming. Current trips get [TripStatus.Now].
 * - past (end < today), most recent first.
 */
fun groupTrips(trips: List<Trip>, today: LocalDate): TripSections {
    val (past, active) = trips.partition { it.endDate.isBefore(today) }
    val activeSorted = active
        .sortedWith(compareBy<Trip>({ it.startDate }, { it.id }))
        .map { ActiveTrip(it, checkNotNull(tripStatus(it, today))) }
    return TripSections(
        next = activeSorted.firstOrNull(),
        upcoming = activeSorted.drop(1),
        past = past.sortedWith(compareByDescending<Trip> { it.startDate }.thenByDescending { it.id }),
    )
}

