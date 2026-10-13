package com.example.planyourtrip.data.place

import com.example.planyourtrip.data.photo.PhotoRules
import com.example.planyourtrip.data.trip.TripPlace

/**
 * What kind of region a suggestion is, so a province never looks like the city with the same name ("Buenos Aires
 * Province" vs "Buenos Aires"). [word] is added to the name in the field (English, like every place name).
 */
enum class RegionKind(val word: String) {
    PROVINCE("Province"), STATE("State"), REGION("Region"), COUNTY("County");

    companion object {
        /** Countries whose first-level divisions (OSM `state`) are called states. */
        private val STATE_COUNTRIES = setOf("US", "AU", "BR", "MX", "IN", "DE", "AT", "MY", "NG", "VE", "SS", "SD")

        /** Countries whose first-level divisions are called provinces ("Buenos Aires Province", "Ontario Province"). */
        private val PROVINCE_COUNTRIES = setOf(
            "AR", "CA", "ZA", "NL", "BE", "CN", "KR", "TH", "PH", "TR", "ID", "VN", "PK", "IR", "CU", "DO", "EC", "BO",
            "PA", "CR", "AF", "IQ", "LK", "KH", "LA", "NP", "PG", "SB",
        )

        /** First-level divisions that are countries of their own (England, Scotland): no word at all. */
        private val NO_WORD_COUNTRIES = setOf("GB")

        /**
         * From Photon's `osm_value` and the country: a `state` is a State, a Province or (anywhere else, e.g.
         * Tuscany, Catalonia) a Region. Null for anything but a region, and for England, Scotland, Wales and
         * Northern Ireland.
         */
        fun of(osmValue: String?, countryCode: String?): RegionKind? = when (osmValue) {
            "state" -> when (countryCode) {
                in NO_WORD_COUNTRIES -> null
                in STATE_COUNTRIES -> STATE
                in PROVINCE_COUNTRIES -> PROVINCE
                else -> REGION
            }
            "province" -> if (countryCode in STATE_COUNTRIES) STATE else PROVINCE
            "region" -> if (countryCode in NO_WORD_COUNTRIES) null else REGION
            "county" -> COUNTY
            else -> null
        }
    }
}

/**
 * A place offered while typing a destination: a city, island, region or country. Names are in English.
 * [region] is the state/county line ("England"), null for countries and when unknown. [regionKind] is set only
 * when the place itself is a province, state, region or county.
 */
data class PlaceSuggestion(
    val name: String,
    val region: String?,
    val countryName: String?,
    /** ISO 3166 code, uppercase ("GB"); null when the service gave none. */
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val regionKind: RegionKind? = null,
) {
    /**
     * The name with its region word ("Buenos Aires Province"), unless the name already ends with it ("Kent County"
     * stays); just the name for anything but a region.
     */
    val fullName: String
        get() {
            val word = regionKind?.word ?: return name
            return if (name.endsWith(" $word", ignoreCase = true) || name.equals(word, ignoreCase = true)) name
            else "$name $word"
        }

    /**
     * What picking it puts in the field: "London, United Kingdom", "Buenos Aires Province, Argentina"; a country (or
     * a place without one) by its name.
     */
    val label: String
        get() = if (countryName == null || countryName.equals(name, ignoreCase = true)) fullName
        else "$fullName, $countryName"

    /** The second line of the row: "England, United Kingdom" (or what of it is known); null when nothing is. */
    val detail: String?
        get() = listOfNotNull(region, countryName?.takeUnless { it.equals(name, ignoreCase = true) })
            .joinToString(", ").ifEmpty { null }

    /**
     * Two suggestions are the same place only when name, region and country all match (any case) and both are (or
     * aren't) the same kind of region.
     */
    val sameness: List<String>
        get() = listOf(
            name.lowercase(), region.orEmpty().lowercase(), countryName.orEmpty().lowercase(),
            regionKind?.name.orEmpty(),
        )

    /** The values the trip stores (normalized: a bad code or coordinate becomes null). */
    fun toTripPlace(): TripPlace = TripPlace(countryCode, latitude, longitude).normalized()
}

/**
 * The same place as [other], for merging the table with Photon: [sameness] matches, or the same name (any case) in
 * the same country (code, else name), of the same kind and within [SAME_PLACE_KM] (when both have coordinates; a country always). The
 * table's region line can differ from Photon's for the same city (Photon's data changes), so the region alone never
 * makes two rows, while two towns of one name in one country stay two.
 */
fun PlaceSuggestion.isSamePlaceAs(other: PlaceSuggestion): Boolean {
    if (sameness == other.sameness) return true
    val country = countryCode?.uppercase() ?: countryName?.lowercase()
    val otherCountry = other.countryCode?.uppercase() ?: other.countryName?.lowercase()
    if (country == null || country != otherCountry || regionKind != other.regionKind) return false
    if (!name.equals(other.name, ignoreCase = true)) return false
    // A country is one place whatever point each source gives it.
    if (name.equals(countryName, ignoreCase = true)) return true
    val lat = latitude ?: return true
    val lon = longitude ?: return true
    val otherLat = other.latitude ?: return true
    val otherLon = other.longitude ?: return true
    return PhotoRules.distanceKm(lat, lon, otherLat, otherLon) <= SAME_PLACE_KM
}

/** How far apart the table's and Photon's points of one place may be (a city's point moves between data versions). */
private const val SAME_PLACE_KM = 50.0

/** Removes repeats of the same place (name, region and country all equal), keeping the first. */
fun List<PlaceSuggestion>.withoutRepeats(): List<PlaceSuggestion> = distinctBy { it.sameness }

/** Finds places for the destination field. Throws on any failure (no network, timeout, bad response). */
fun interface PlaceSearch {
    suspend fun search(query: String): List<PlaceSuggestion>
}
