package com.example.planyourtrip.data.photo

import com.example.planyourtrip.data.trip.Trip
import java.util.Locale

/**
 * What to find a photo of: the trip's destination text and, for a place picked from the suggestions, its
 * coordinates (both or neither).
 */
data class PhotoQuery(
    val text: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    /** The photo's row in Room: one per destination text + coordinates. */
    val key: String get() = photoKey(text, latitude, longitude)

    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}

/**
 * Room key of a destination's photo: the text trimmed, lowercased and with single spaces, then "|" and the
 * coordinates rounded to 3 decimals (about 100 m), or nothing for free text. "Rome" and " rome " share a photo;
 * "London, Canada" picked and typed are different destinations.
 */
fun photoKey(text: String, latitude: Double?, longitude: Double?): String {
    val name = text.trim().lowercase(Locale.ROOT).split(WHITESPACE).joinToString(" ")
    if (latitude == null || longitude == null) return "$name|"
    return "$name|" + String.format(Locale.ROOT, "%.3f,%.3f", latitude, longitude)
}

private val WHITESPACE = Regex("\\s+")

/** The photo lookup of this trip's destination (an edited destination is a new query, so a new photo). */
val Trip.photoQuery: PhotoQuery get() = PhotoQuery(destination.trim(), latitude, longitude)

/** A found photo, as the screens use it. */
data class DestinationPhoto(
    /** About 960 px wide: the Next trip card and the trip header. */
    val cardUrl: String,
    /** About 250 px wide: the Upcoming and Past cards' thumbnail. */
    val thumbUrl: String,
)

/** Which step of the lookup gave the photo (logged, so fallbacks can be reviewed). */
enum class PhotoOrigin { LEAD, WIKIDATA, PAGE_IMAGES }

sealed interface PhotoResult {
    data class Found(
        val cardUrl: String,
        val thumbUrl: String,
        /** The image's description page (author and license), kept for a photo credit later. */
        val filePageUrl: String?,
        val origin: PhotoOrigin,
        val fileName: String,
        val pageTitle: String,
    ) : PhotoResult

    data object NotFound : PhotoResult
}

/** The service answered with something unreadable. Remembered like "nothing found" (retried after a week). */
class PhotoLookupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Finds a photo of a destination. The only thing screens and storage know about where photos come from, so the
 * provider can be replaced. Throws [java.io.IOException] for connection failures (offline, timeout, HTTP error) and
 * [PhotoLookupException] for an unreadable answer.
 */
fun interface DestinationPhotoSource {
    suspend fun find(query: PhotoQuery): PhotoResult
}
