package com.example.planyourtrip.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Trip
import java.time.LocalDate

/** "12–17 Nov 2026 · 6 days" */
@Composable
fun datesWithLength(trip: Trip): String = datesWithLength(trip.startDate, trip.endDate)

/** "12–17 Nov 2026 · 6 days" for any date range (an invite's trip). */
@Composable
fun datesWithLength(start: LocalDate, end: LocalDate): String {
    val length = TripFormatting.lengthInDays(start, end)
    return stringResource(
        R.string.home_dates_with_length,
        TripFormatting.dateRange(start, end),
        pluralStringResource(R.plurals.trip_length_days, length, length),
    )
}

/** "Now" or "In 120 days" */
@Composable
fun statusLabel(status: TripStatus): String = when (status) {
    TripStatus.Now -> stringResource(R.string.home_now)
    is TripStatus.InDays -> pluralStringResource(R.plurals.countdown_in_days, status.days, status.days)
}
