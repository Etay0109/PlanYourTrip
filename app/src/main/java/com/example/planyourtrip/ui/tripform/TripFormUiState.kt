package com.example.planyourtrip.ui.tripform

import com.example.planyourtrip.data.place.PlaceSuggestion
import com.example.planyourtrip.data.trip.TripPlace
import com.example.planyourtrip.ui.common.CurrencyPickerState
import java.time.LocalDate

data class TripFormUiState(
    /** True when changing an existing trip ("Edit Trip"), false for a new trip. */
    val isEditing: Boolean = false,
    /** While editing, true until the trip's current values are loaded into the fields. */
    val isLoading: Boolean = false,
    /** The typed destination as the ViewModel last got it; the field keeps its own text and starts from this. */
    val destination: String = "",
    /** Places matching the typed destination (empty = no list shown): the built-in table's first, then Photon's. */
    val suggestions: List<PlaceSuggestion> = emptyList(),
    /** True while Photon is being asked (after the typing pause, until it answers or fails): the thin bar shows. */
    val isSearching: Boolean = false,
    /**
     * True when the lookup failed because the device is offline and the built-in table had nothing for the text:
     * "Connect to the internet to search more places" shows in place of the list. Cleared by the next keystroke.
     */
    val searchNeedsInternet: Boolean = false,
    /**
     * The picked suggestion's country and coordinates (or the edited trip's stored ones); null once the text is
     * edited, until a suggestion is picked again. Free text is saved without them.
     */
    val place: TripPlace? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /**
     * Optional destination currency ("EUR"); null = none. Chosen by the user, or, for a new trip, preselected from a
     * picked place's country ([currencyPreselected]); never guessed from free text.
     */
    val currencyCode: String? = null,
    /** True while [currencyCode] was preselected from the picked place and not touched since. */
    val currencyPreselected: Boolean = false,
    /** Full name shown next to the code ("Euro"), when known. */
    val currencyName: String? = null,
    /** The currency picker while it is open. */
    val currencyPicker: CurrencyPickerState? = null,
    val destinationError: TripFieldError? = null,
    val startError: TripFieldError? = null,
    val endError: TripFieldError? = null,
    val isSaving: Boolean = false,
    /** Set once the trip is stored; the screen then navigates back. */
    val saved: Boolean = false,
)
