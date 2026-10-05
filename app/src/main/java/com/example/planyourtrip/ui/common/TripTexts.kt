package com.example.planyourtrip.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Trip

/** "12–17 Nov 2026 · 6 days" */
@Composable
fun datesWithLength(trip: Trip): String {
    val length = TripFormatting.lengthInDays(trip.startDate, trip.endDate)
    return stringResource(
        R.string.home_dates_with_length,
        TripFormatting.dateRange(trip.startDate, trip.endDate),
        pluralStringResource(R.plurals.trip_length_days, length, length),
    )
}

/** "Now" or "In 120 days" */
@Composable
fun statusLabel(status: TripStatus): String = when (status) {
    TripStatus.Now -> stringResource(R.string.home_now)
    is TripStatus.InDays -> pluralStringResource(R.plurals.countdown_in_days, status.days, status.days)
}
