package com.example.planyourtrip.data.place

import com.example.planyourtrip.data.trip.TripPlace

/**
 * A place offered while typing a destination: a city, island, region or country. Names are in English.
 * [region] is the state/county line ("England"), null for countries and when unknown.
 */
data class PlaceSuggestion(
    val name: String,
    val region: String?,
    val countryName: String?,
    /** ISO 3166 code, uppercase ("GB"); null when the service gave none. */
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
) {
    /** What picking it puts in the field: "London, United Kingdom"; a country (or a place without one) by its name. */
    val label: String
        get() = if (countryName == null || countryName.equals(name, ignoreCase = true)) name else "$name, $countryName"

    /** The second line of the row: "England, United Kingdom" (or what of it is known); null when nothing is. */
    val detail: String?
        get() = listOfNotNull(region, countryName?.takeUnless { it.equals(name, ignoreCase = true) })
            .joinToString(", ").ifEmpty { null }

    /** Two suggestions are the same place only when name, region and country all match (any case). */
    val sameness: Triple<String, String, String>
        get() = Triple(name.lowercase(), region.orEmpty().lowercase(), countryName.orEmpty().lowercase())

    /** The values the trip stores (normalized: a bad code or coordinate becomes null). */
    fun toTripPlace(): TripPlace = TripPlace(countryCode, latitude, longitude).normalized()
}

/** Removes repeats of the same place (name, region and country all equal), keeping the first. */
fun List<PlaceSuggestion>.withoutRepeats(): List<PlaceSuggestion> = distinctBy { it.sameness }

/** Finds places for the destination field. Throws on any failure (no network, timeout, bad response). */
fun interface PlaceSearch {
    suspend fun search(query: String): List<PlaceSuggestion>
}
