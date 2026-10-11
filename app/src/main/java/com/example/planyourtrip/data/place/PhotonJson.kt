package com.example.planyourtrip.data.place

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parser for Photon's GeoJSON answer (pure, unit-tested). Keeps only places a trip goes to: countries, regions,
 * cities, towns, villages and islands (`osm_key` place); streets, shops, houses and neighbourhoods are dropped.
 * Malformed JSON throws; a single unreadable feature is skipped.
 */
object PhotonJson {
    /** The OSM `place=*` values offered as destinations. */
    val PLACE_TYPES = setOf(
        "country", "state", "region", "province", "county", "city", "town", "village", "municipality",
        "island", "archipelago",
    )

    private val COUNTRY_CODE = Regex("^[A-Z]{2}$")

    fun parse(json: String): List<PlaceSuggestion> =
        Json.parseToJsonElement(json).jsonObject.getValue("features").jsonArray
            .mapNotNull { runCatching { feature(it.jsonObject) }.getOrNull() }
            .withoutRepeats()

    private fun feature(feature: JsonObject): PlaceSuggestion? {
        val props = feature.getValue("properties").jsonObject
        fun text(key: String) = props[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        if (text("osm_key") != "place" || text("osm_value") !in PLACE_TYPES) return null
        val name = text("name") ?: return null
        val country = text("country")
        val isCountry = text("osm_value") == "country"
        // GeoJSON order: [longitude, latitude].
        val coordinates = feature["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
        val longitude = coordinates?.getOrNull(0)?.jsonPrimitive?.doubleOrNull
        val latitude = coordinates?.getOrNull(1)?.jsonPrimitive?.doubleOrNull
        val coordinatesOk = latitude != null && longitude != null &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
        return PlaceSuggestion(
            name = name,
            region = if (isCountry) null else (text("state") ?: text("county"))?.takeUnless { it.equals(name, true) },
            countryName = country,
            countryCode = text("countrycode")?.uppercase()?.takeIf { it.matches(COUNTRY_CODE) },
            latitude = if (coordinatesOk) latitude else null,
            longitude = if (coordinatesOk) longitude else null,
        )
    }
}
