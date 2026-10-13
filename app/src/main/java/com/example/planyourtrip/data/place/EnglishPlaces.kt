package com.example.planyourtrip.data.place

import java.text.Normalizer
import java.util.Locale

/**
 * Latin input for the destination field (pure, unit-tested in EnglishPlacesTest): the same built-in table, matched on
 * its English names, so popular places show at once, before (and without) Photon. Accents, case, dashes and extra
 * spaces don't matter ("ile de" = "Île-de"); "par" finds Paris, "york" New York, "paris, f" "Paris, France".
 */
object EnglishPlaces {
    private val MARKS = Regex("\\p{Mn}+")
    private val DASHES = Regex("[\\-‐-―]")
    private val SPACES = Regex("\\s+")

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(MARKS, "")
        .replace(DASHES, " ")
        .replace(SPACES, " ")
        .trim()
        .lowercase(Locale.ROOT)

    /**
     * Places whose English name (or the label a pick puts in the field, "Paris, France") starts with [query], also at
     * a later word of the name. Exact names first, then names starting with it, then later words; ties in table
     * order (most popular first). At most [limit].
     */
    fun search(
        query: String,
        table: List<HebrewPlace> = HEBREW_PLACE_TABLE,
        limit: Int = HebrewPlaces.MAX_MATCHES,
    ): List<PlaceSuggestion> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return table.withIndex()
            .mapNotNull { (index, place) ->
                val name = normalize(place.suggestion.name)
                val rank = when {
                    name == q -> 0
                    name.startsWith(q) || normalize(place.suggestion.label).startsWith(q) -> 1
                    name.contains(" $q") -> 2
                    else -> return@mapNotNull null
                }
                Triple(rank, index, place.suggestion)
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }
            // A place listed under two Hebrew names shows once.
            .withoutRepeats()
            .take(limit)
    }
}
