package com.example.planyourtrip.data.place

import kotlinx.coroutines.CancellationException
import java.util.Locale

/**
 * The suggestions for what was typed in the destination field. Latin text → Photon. Text with Hebrew letters → the
 * built-in Hebrew table first (works offline), then Photon's results for the text itself (places in Israel have
 * Hebrew names there). Never throws for a failed lookup: it just returns fewer (or no) suggestions.
 */
class DestinationSuggestions(
    private val search: PlaceSearch,
    private val hebrewTable: List<HebrewPlace> = HEBREW_PLACE_TABLE,
) {
    suspend fun suggest(text: String): List<PlaceSuggestion> {
        val query = text.trim()
        if (query.length < MIN_QUERY_LENGTH) return emptyList()
        val local = if (HebrewPlaces.containsHebrew(query)) HebrewPlaces.search(query, hebrewTable) else emptyList()
        val remote = try {
            search.search(query)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // No network, timeout, server error or a bad answer: no list from Photon, never an error message.
            emptyList()
        }
        return (local + remote).withoutRepeats().take(MAX_SUGGESTIONS)
    }

    companion object {
        /** Lookups start at 2 characters. */
        const val MIN_QUERY_LENGTH = 2
        const val MAX_SUGGESTIONS = 6
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
