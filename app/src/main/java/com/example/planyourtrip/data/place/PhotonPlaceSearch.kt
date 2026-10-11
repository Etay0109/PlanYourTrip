package com.example.planyourtrip.data.place

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Place search on Photon (https://photon.komoot.io, OpenStreetMap data, no API key). The only Photon HTTP code.
 * Names come in English (`lang=en`); only `place=*` objects are asked for. Cancelling the call (a new keystroke)
 * disconnects the running request instead of letting it finish.
 */
class PhotonPlaceSearch(
    /** "PlanYourTrip/1.0": Photon's usage policy asks every app to identify itself. */
    appVersion: String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlaceSearch {

    private val userAgent = "PlanYourTrip/$appVersion (Android; com.example.planyourtrip)"

    override suspend fun search(query: String): List<PlaceSuggestion> = withContext(ioDispatcher) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val connection = URL("$BASE_URL?q=$q&lang=en&limit=$LIMIT&osm_tag=place").openConnection() as HttpURLConnection
        val body = async { read(connection) }
        try {
            PhotonJson.parse(body.await())
        } catch (e: CancellationException) {
            // A blocked socket read ignores coroutine cancellation: closing the connection ends it right away.
            connection.disconnect()
            throw e
        }
    }

    private fun read(connection: HttpURLConnection): String = try {
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", userAgent)
        val status = connection.responseCode
        if (status !in 200..299) throw IOException("HTTP $status from Photon")
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }

    private companion object {
        const val BASE_URL = "https://photon.komoot.io/api/"
        const val LIMIT = 10
        const val TIMEOUT_MILLIS = 10_000
    }
}
