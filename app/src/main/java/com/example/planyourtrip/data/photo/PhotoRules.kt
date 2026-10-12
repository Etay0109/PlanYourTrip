package com.example.planyourtrip.data.photo

import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A search result: one Wikipedia page with its coordinates and lead image. */
data class WikiPage(
    val title: String,
    /** Rank in the search results (1 = best); the API returns pages in another order. */
    val index: Int,
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** GeoData's coordinate type ("city", "country", "adm1st", …); null when the page doesn't say. */
    val coordinateType: String? = null,
    val disambiguation: Boolean = false,
    /** "Q84"; null when the page has no Wikidata item. */
    val wikidataId: String? = null,
    val leadImage: WikiImage? = null,
)

/** An image file with what the rules need. [fileName] has no "File:" prefix. */
data class WikiImage(
    val fileName: String,
    val width: Int,
    val height: Int,
    /** From the service; null = read from the file name's extension. */
    val mime: String? = null,
    /** The scaled image (960 or 250 px wide) or the original when it is smaller. */
    val url: String? = null,
    val descriptionUrl: String? = null,
)

/** Which page and which image make a destination's photo (pure, unit-tested). */
object PhotoRules {
    const val MIN_WIDTH = 400
    const val MIN_ASPECT = 0.6
    /** Wide panoramas and skylines are fine: the card is about 2.5:1 and the image is cropped to fill it. */
    const val MAX_ASPECT = 4.0

    /** A page this close to the trip's place is the same place: cities and anything without a type. */
    const val PLACE_KM = 50.0
    const val REGION_KM = 400.0
    /** A country's Wikipedia point is often its capital, far from the centroid the suggestions give. */
    const val COUNTRY_KM = 4_000.0

    private val REGION_TYPES = setOf("state", "adm1st", "adm2nd", "adm3rd", "isle", "region")
    /** Coordinate types of places (a page with another type, "edu" or "landmark", is never the destination). */
    private val PLACE_TYPES = REGION_TYPES + setOf("city", "country", "continent")
    private val COUNTRY_TYPES = setOf("country", "continent")

    private const val JPEG = "image/jpeg"
    /**
     * Photos only: a place's PNG lead image is almost always a map, a flag or a diagram (Santorini's is a PNG map of
     * the municipality, with no telling word in its name), and SVG, GIF and TIFF never are photos.
     */
    private val PHOTO_TYPES = setOf(JPEG, "image/webp")

    /**
     * Words in a file name that mean it isn't a photo of the place (flags, coats of arms, maps, logos, montages).
     * Matched as whole words, so "Mapleton" passes and "Seal_Beach" doesn't.
     */
    val BLOCKED_WORDS = setOf(
        "flag", "flags", "coat", "arms", "coa", "emblem", "seal", "logo", "map", "maps", "locator", "location",
        "locmap", "orthographic", "montage", "collage", "collection", "mosaic", "composite", "banner", "symbol",
        "icon", "diagram", "wappen", "escudo", "blason", "collagem", "montaje", "montagem", "montaggio",
        // Hebrew: flag, symbol / emblem, map (and "map of"), montage, collage (the geresh is not part of a word).
        "דגל", "סמל", "מפה", "מפת", "מונטאז", "קולאז",
    )

    /**
     * Collage words also caught when glued to other words ("DubaiCollage", "Buenosairesmontaje"): English, Spanish,
     * Portuguese and Italian.
     */
    val GLUED_BLOCKED_WORDS = listOf("collage", "collagem", "montage", "montaje", "montagem", "montaggio")

    private val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")

    fun mimeOf(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "jpg", "jpeg", "jpe" -> JPEG
        "png" -> "image/png"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "gif" -> "image/gif"
        "tif", "tiff" -> "image/tiff"
        else -> null
    }

    /** The word of [fileName] that blocks it (whole word, or a glued collage word), or null. */
    fun blockedWord(fileName: String): String? =
        fileName.substringBeforeLast('.').lowercase(Locale.ROOT).split(WORD_SEPARATOR).firstOrNull { word ->
            word in BLOCKED_WORDS || GLUED_BLOCKED_WORDS.any { it in word }
        }

    fun hasBlockedName(fileName: String): Boolean = blockedWord(fileName) != null

    /**
     * Why [image] isn't a photo of the place (logged), or null when it is: JPEG or WebP, ≥ 400 px wide, a usual
     * shape, and no blocked word in its name.
     */
    fun rejection(image: WikiImage): String? {
        val type = image.mime ?: mimeOf(image.fileName)
        if (type !in PHOTO_TYPES) return "type ${type ?: "unknown"}"
        if (image.width < MIN_WIDTH) return "${image.width} px wide"
        if (image.height <= 0) return "no height"
        val ratio = image.width.toDouble() / image.height
        if (ratio !in MIN_ASPECT..MAX_ASPECT) return String.format(Locale.ROOT, "shape %.2f", ratio)
        return blockedWord(image.fileName)?.let { "blocked word '$it'" }
    }

    fun isSuitable(image: WikiImage): Boolean = rejection(image) == null

    /** Taller than wide: often a collage of several photos, so the Wikidata image is tried first. */
    fun isPortrait(image: WikiImage): Boolean = image.height > image.width

    /** The last fallback, among all the page's images: only JPEG photos in landscape shape. */
    fun isPageImageCandidate(image: WikiImage): Boolean =
        isSuitable(image) && (image.mime ?: mimeOf(image.fileName)) == JPEG && image.width > image.height

    /** The largest suitable landscape JPEG of the page, or null. */
    fun bestPageImage(images: List<WikiImage>): WikiImage? =
        images.filter { isPageImageCandidate(it) && it.url != null }
            .maxByOrNull { it.width.toLong() * it.height }

    /** How far a page's coordinates may be from the trip's place, by the page's coordinate type. */
    fun maxDistanceKm(coordinateType: String?): Double = when (coordinateType?.lowercase(Locale.ROOT)) {
        in COUNTRY_TYPES -> COUNTRY_KM
        in REGION_TYPES -> REGION_KM
        else -> PLACE_KM
    }

    /** Great-circle distance (haversine, mean Earth radius). */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * The page that is the destination, in search order, skipping disambiguation pages. With coordinates: the first
     * page whose own coordinates are close enough ("London, Canada" never takes London, England); without: the first
     * page. Only that page is used, even when it has no photo, so a nearby landmark never stands in for the city.
     */
    fun choosePage(pages: List<WikiPage>, query: PhotoQuery): WikiPage? {
        val candidates = pages.sortedBy { it.index }.filterNot { it.disambiguation }
        if (!query.hasCoordinates) return candidates.firstOrNull()
        return candidates.firstOrNull { page ->
            val lat = page.latitude ?: return@firstOrNull false
            val lon = page.longitude ?: return@firstOrNull false
            distanceKm(query.latitude!!, query.longitude!!, lat, lon) <= maxDistanceKm(page.coordinateType)
        }
    }

    /** Type words the destination suggestions add to a region's name ("Buenos Aires Province"). */
    private val REGION_WORDS = Regex("\\s+(province|state|region|county)$", RegexOption.IGNORE_CASE)

    /**
     * The name searched again when no page passes [choosePage]: the text before the first comma, without a region
     * word ("Buenos Aires Province, Argentina" and "Buenos Aires, Argentina" → "Buenos Aires"); null when too short.
     */
    fun fallbackName(text: String): String? =
        text.substringBefore(',').trim().replace(REGION_WORDS, "").trim().takeIf { it.length >= 2 }

    /**
     * The fallback for a picked province or region whose point is far from its Wikipedia page's point: in search
     * order, the first page whose title starts with [name], whose coordinate type is a place (or not given), and
     * which is within [REGION_KM] (or its type's larger limit). "London" with Ontario's coordinates still never takes
     * London, England (5,700 km away).
     */
    fun chooseNamedNearby(pages: List<WikiPage>, query: PhotoQuery, name: String): WikiPage? {
        if (!query.hasCoordinates) return null
        return pages.sortedBy { it.index }.filterNot { it.disambiguation }.firstOrNull { page ->
            val lat = page.latitude ?: return@firstOrNull false
            val lon = page.longitude ?: return@firstOrNull false
            val type = page.coordinateType?.lowercase(Locale.ROOT)
            page.title.startsWith(name, ignoreCase = true) &&
                (type == null || type in PLACE_TYPES) &&
                distanceKm(query.latitude!!, query.longitude!!, lat, lon) <=
                maxOf(REGION_KM, maxDistanceKm(type))
        }
    }
}

/** True when the text has Hebrew letters (searched on Hebrew Wikipedia first). */
fun hasHebrew(text: String): Boolean = text.any { it in 'א'..'ת' }
