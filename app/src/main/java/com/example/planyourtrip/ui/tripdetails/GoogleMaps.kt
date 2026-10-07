package com.example.planyourtrip.ui.tripdetails

import java.net.URLEncoder

// Pure (unit-tested in GoogleMapsTest): the links the Overview's map card and the Plan cards' "Open in Maps" open.

private const val GOOGLE_MAPS_SEARCH = "https://www.google.com/maps/search/?api=1&query="

/**
 * Google Maps' search URL for [destination] ("Rome, Italy" → "…&query=Rome%2C%20Italy"). The text is trimmed and
 * percent-encoded as UTF-8 (spaces as %20, so Hebrew, "&", "#" and "+" keep their meaning). Opens the Google Maps
 * app when it is installed, otherwise the browser.
 */
fun googleMapsSearchUrl(destination: String): String =
    GOOGLE_MAPS_SEARCH + URLEncoder.encode(destination.trim(), "UTF-8").replace("+", "%20")

/**
 * The search URL for an activity's [location] in the trip's [destination]: "<location>, <destination>", so
 * "Colosseum" finds the one in Rome. The destination is left out when it is blank or the location already
 * mentions it ("Colosseum, Rome" in a trip to "Rome").
 */
fun googleMapsActivityUrl(location: String, destination: String): String {
    val where = location.trim()
    val city = destination.trim()
    val query = if (city.isEmpty() || where.contains(city, ignoreCase = true)) where else "$where, $city"
    return googleMapsSearchUrl(query)
}
