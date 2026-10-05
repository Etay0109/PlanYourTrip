package com.example.planyourtrip.ui.tripform

import com.example.planyourtrip.ui.common.CurrencyPickerState
import java.time.LocalDate

data class TripFormUiState(
    /** True when changing an existing trip ("Edit Trip"), false for a new trip. */
    val isEditing: Boolean = false,
    /** While editing, true until the trip's current values are loaded into the fields. */
    val isLoading: Boolean = false,
    val destination: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    /** Optional destination currency the user picked ("EUR"); null = none. Never guessed from the destination. */
    val currencyCode: String? = null,
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
