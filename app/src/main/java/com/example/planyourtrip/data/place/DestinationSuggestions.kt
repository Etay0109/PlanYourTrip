package com.example.planyourtrip.data.place

import kotlinx.coroutines.CancellationException
import java.util.Locale

/**
 * The suggestions for what was typed in the destination field, in two steps:
 * - [instant]: the built-in table (popular places abroad), matched on its Hebrew names for text with Hebrew letters and
 *   on its English names otherwise. No network, so the screen shows it on every keystroke.
 * - [remote]: Photon (places anywhere; for Hebrew text the places in Israel, which have Hebrew names there).
 * [merge] puts Photon's places under the instant ones. A failed lookup never throws: it just adds nothing.
 */
class DestinationSuggestions(
    private val search: PlaceSearch,
    private val table: List<HebrewPlace> = HEBREW_PLACE_TABLE,
) {
    /** The table's places for [text] (empty below [MIN_QUERY_LENGTH] characters). */
    fun instant(text: String): List<PlaceSuggestion> {
        val query = text.trim()
        if (query.length < MIN_QUERY_LENGTH) return emptyList()
        val found = if (HebrewPlaces.containsHebrew(query)) HebrewPlaces.search(query, table)
        else EnglishPlaces.search(query, table)
        return found.take(MAX_SUGGESTIONS)
    }

    /** Photon's places for [text]; null when the lookup failed (no network, timeout, server error, bad answer). */
    suspend fun remote(text: String): List<PlaceSuggestion>? {
        val query = text.trim()
        if (query.length < MIN_QUERY_LENGTH) return emptyList()
        return try {
            search.search(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Both steps at once: [instant], then [remote] merged under it. */
    suspend fun suggest(text: String): List<PlaceSuggestion> = merge(instant(text), remote(text).orEmpty())

    companion object {
        /** Lookups start at 2 characters. */
        const val MIN_QUERY_LENGTH = 2
        const val MAX_SUGGESTIONS = 6

        /**
         * [instant] rows first, exactly where they were (so the list doesn't jump when Photon answers), then the
         * [remote] places that aren't one of them (the same place in the same country: [isSamePlaceAs]) or a repeat
         * of each other. At most [MAX_SUGGESTIONS].
         */
        fun merge(instant: List<PlaceSuggestion>, remote: List<PlaceSuggestion>): List<PlaceSuggestion> {
            val first = instant.take(MAX_SUGGESTIONS)
            val added = remote.filter { place -> first.none { it.isSamePlaceAs(place) } }
            return (first + added).withoutRepeats().take(MAX_SUGGESTIONS)
        }
    }
}

/**
 * The currency of a country ("GB" → "GBP", "AR" → "ARS", "FR" → "EUR") from the platform's data; null when unknown or
 * when the country has none. [CURRENCY_CHANGES] first: the platform's data (the JVM's, or an older Android's ICU) can
 * still name a replaced currency.
 */
fun countryCurrency(countryCode: String): String? {
    CURRENCY_CHANGES[countryCode.uppercase(Locale.ROOT)]?.let { return it }
    return runCatching {
        java.util.Currency.getInstance(Locale.Builder().setRegion(countryCode).build())?.currencyCode
    }.getOrNull()?.takeIf { it.length == 3 && it != "XXX" }
}

/**
 * Countries whose currency changed recently, so platform data may still give the old one: Bulgaria (euro, 2026),
 * Croatia (euro, 2023), Venezuela (VES), Sierra Leone (SLE), Zimbabwe (ZWG), Mauritania (MRU), São Tomé (STN),
 * Belarus (BYN), Curaçao and Sint Maarten (Caribbean guilder, 2025).
 */
private val CURRENCY_CHANGES = mapOf(
    "BG" to "EUR", "HR" to "EUR", "VE" to "VES", "SL" to "SLE", "ZW" to "ZWG", "MR" to "MRU", "ST" to "STN",
    "BY" to "BYN", "CW" to "XCG", "SX" to "XCG",
)
