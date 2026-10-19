package com.example.planyourtrip.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.account.AccountState
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import java.time.LocalDate

// ---------- Previews (sample data lives only here, never in the database) ----------

private val previewToday = LocalDate.of(2026, 10, 5)

private val previewTrips = listOf(
    Trip(1, "Rome, Italy", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY),
    Trip(2, "Athens, Greece", LocalDate.of(2027, 2, 3), LocalDate.of(2027, 2, 7), TripColor.SKY),
    Trip(3, "Lisbon, Portugal", LocalDate.of(2027, 4, 20), LocalDate.of(2027, 4, 26), TripColor.PEACH),
    Trip(4, "Prague, Czech Republic", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), TripColor.MINT),
)

private fun previewState(trips: List<Trip>) = HomeUiState.Content(
    userName = "Etay Zahavi",
    firstName = "Etay",
    sections = groupTrips(trips, previewToday),
)

private val previewAccount = AccountState(userName = "Etay Zahavi", email = "traveler@example.com", initials = "EZ")

@Preview(name = "Home", widthDp = 411, heightDp = 891)
@Composable
internal fun HomePreview() {
    PlanYourTripTheme(darkTheme = false) {
        HomeContent(previewState(previewTrips), previewAccount, onAddTrip = {}, onTripClick = {}, onShareTrip = {})
    }
}

@Preview(name = "Home – narrow, large font", widthDp = 320, heightDp = 800, fontScale = 1.5f)
@Composable
internal fun HomeNarrowPreview() {
    PlanYourTripTheme(darkTheme = false) {
        HomeContent(
            previewState(
                previewTrips + listOf(
                    Trip(
                        5, "Reykjavík, Iceland", LocalDate.of(2027, 12, 28), LocalDate.of(2028, 1, 3),
                        TripColor.LAVENDER,
                    ),
                    // Long name and long dates next to the three-dots button: nothing may be cut off.
                    Trip(
                        6, "San Carlos de Bariloche, Argentina", LocalDate.of(2026, 3, 28),
                        LocalDate.of(2026, 4, 3), TripColor.ROSE,
                    ),
                )
            ),
            previewAccount,
            onAddTrip = {}, onTripClick = {}, onShareTrip = {},
        )
    }
}

@Preview(name = "Home – dark", widthDp = 411, heightDp = 891)
@Composable
internal fun HomeDarkPreview() {
    PlanYourTripTheme(darkTheme = true) {
        HomeContent(previewState(previewTrips), previewAccount, onAddTrip = {}, onTripClick = {}, onShareTrip = {})
    }
}

@Preview(name = "Home – empty", widthDp = 411, heightDp = 891)
@Composable
internal fun HomeEmptyPreview() {
    PlanYourTripTheme(darkTheme = false) {
        HomeContent(previewState(emptyList()), previewAccount, onAddTrip = {}, onTripClick = {}, onShareTrip = {})
    }
}
