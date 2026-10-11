package com.example.planyourtrip.ui.home

import com.example.planyourtrip.data.photo.DestinationPhoto
import com.example.planyourtrip.data.trip.Trip

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        /** Display name, or the email prefix when there is no name (see `greetingName()`). */
        val userName: String,
        val firstName: String,
        val sections: TripSections,
        /** The signed-in account's uid ("Delete trip" for my own trips, "Leave trip" for others' shared trips). */
        val uid: String = "",
        /** Destination photos by trip id; a trip without one shows the gradient / letter square. */
        val photos: Map<Long, DestinationPhoto> = emptyMap(),
        /** Today's year: the cards' dates leave it out for trips in this year ("19–24 Oct"); null = always shown. */
        val currentYear: Int? = null,
    ) : HomeUiState {
        /** My own trip (or one without a known owner); a shared trip someone else owns is left, not deleted. */
        fun isOwner(trip: Trip): Boolean = trip.ownerId.isBlank() || trip.ownerId == uid
    }
}
