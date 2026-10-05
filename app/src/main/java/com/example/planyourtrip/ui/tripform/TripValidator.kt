package com.example.planyourtrip.ui.tripform

import java.time.LocalDate

/** Validation error codes for the New Trip form, mapped to string resources in the UI. */
enum class TripFieldError {
    EMPTY_DESTINATION,
    MISSING_START,
    MISSING_END,
    END_BEFORE_START,
}

/** Pure validation rules (unit-tested). Each function returns null when valid. */
object TripValidator {
    fun validateDestination(destination: String): TripFieldError? =
        if (destination.isBlank()) TripFieldError.EMPTY_DESTINATION else null

    fun validateStart(start: LocalDate?): TripFieldError? =
        if (start == null) TripFieldError.MISSING_START else null

    /** A one-day trip (end == start) is valid. */
    fun validateEnd(start: LocalDate?, end: LocalDate?): TripFieldError? = when {
        end == null -> TripFieldError.MISSING_END
        start != null && end.isBefore(start) -> TripFieldError.END_BEFORE_START
        else -> null
    }
}
