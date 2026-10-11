package com.example.planyourtrip.data.trip

/**
 * Where a trip goes, when the destination was picked from the suggestions: the country's ISO 3166 code ("GB") and
 * the place's coordinates. Every value is optional (free text has none); latitude and longitude come together.
 */
data class TripPlace(
    val countryCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /**
     * The values Room, Firestore and the rules accept: a code of two letters A–Z (uppercased), coordinates finite,
     * within range and both present; anything else becomes null, so a bad value never stops a save or a sync.
     */
    fun normalized(): TripPlace {
        val code = countryCode?.trim()?.uppercase()?.takeIf { it.matches(COUNTRY_CODE) }
        val coordinatesOk = latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
        return TripPlace(
            countryCode = code,
            latitude = if (coordinatesOk) latitude else null,
            longitude = if (coordinatesOk) longitude else null,
        )
    }

    companion object {
        val NONE = TripPlace()
        private val COUNTRY_CODE = Regex("^[A-Z]{2}$")
    }
}

val Trip.place: TripPlace get() = TripPlace(countryCode, latitude, longitude)
