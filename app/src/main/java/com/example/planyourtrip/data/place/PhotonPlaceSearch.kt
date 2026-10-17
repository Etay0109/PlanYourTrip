package com.example.planyourtrip.data.place

import com.example.planyourtrip.data.http.HttpGet
import com.example.planyourtrip.data.http.appUserAgent
import java.net.URLEncoder

/**
 * Place search on Photon (https://photon.komoot.io, OpenStreetMap data, no API key). The only Photon HTTP code.
 * Names come in English (`lang=en`); only `place=*` objects are asked for. Cancelling the call (a new keystroke)
 * cancels the running request ([HttpGet]) instead of letting it finish.
 */
class PhotonPlaceSearch(
    /** Sent in the User-Agent: Photon's usage policy asks every app to identify itself. */
    appVersion: String,
    private val http: HttpGet,
) : PlaceSearch {

    private val userAgent = appUserAgent(appVersion)

    override suspend fun search(query: String): List<PlaceSuggestion> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return PhotonJson.parse(http.get("$BASE_URL?q=$q&lang=en&limit=$LIMIT&osm_tag=place", userAgent))
    }

    private companion object {
        const val BASE_URL = "https://photon.komoot.io/api/"
        const val LIMIT = 10
    }
}
