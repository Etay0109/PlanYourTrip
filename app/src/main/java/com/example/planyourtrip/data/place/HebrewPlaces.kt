package com.example.planyourtrip.data.place

/**
 * A place in the built-in Hebrew table: its Hebrew name, other spellings, and the English suggestion it stands for
 * (name, region, country, code and coordinates, looked up once on Photon when the table was made).
 */
data class HebrewPlace(val hebrew: String, val aliases: List<String>, val suggestion: PlaceSuggestion) {
    val names: List<String> get() = listOf(hebrew) + aliases
}

/**
 * Hebrew input for the destination field (pure, unit-tested in HebrewPlacesTest). Photon has no Hebrew names for
 * places abroad ("לונדון" finds only streets in Israel), so Hebrew text is matched against [HEBREW_PLACE_TABLE]
 * on the device, also offline.
 */
object HebrewPlaces {
    private val HEBREW_LETTER = Regex("[א-ת]")
    private val NIQQUD = Regex("[֑-ֽֿ-ׇ]")
    private val QUOTES = Regex("[׳״'\"`’‘“”]")
    private val DASHES = Regex("[־\\-‐-―]")
    private val SPACES = Regex("\\s+")
    private val FINAL_LETTERS = mapOf('ך' to 'כ', 'ם' to 'מ', 'ן' to 'נ', 'ף' to 'פ', 'ץ' to 'צ')

    fun containsHebrew(text: String): Boolean = HEBREW_LETTER.containsMatchIn(text)

    /**
     * The form names are compared in: no niqqud, no geresh/gershayim or quotes ("צ׳כיה" = "צכיה"), a maqaf or dash
     * is a space ("ניו-יורק" = "ניו יורק"), final letters are the regular ones (so "לונדו" is a start of "לונדון"),
     * single spaces, lowercase.
     */
    fun normalize(text: String): String = text
        .replace(DASHES, " ")
        .replace(NIQQUD, "")
        .replace(QUOTES, "")
        .map { FINAL_LETTERS[it] ?: it }.joinToString("")
        .replace(SPACES, " ")
        .trim()
        .lowercase()

    /**
     * Places whose Hebrew name or another spelling starts with [query] (also at a later word: "יורק" finds New
     * York). Exact names first, then names starting with it, then later words; ties in table order (most popular
     * first). At most [limit].
     */
    fun search(
        query: String,
        table: List<HebrewPlace> = HEBREW_PLACE_TABLE,
        limit: Int = MAX_MATCHES,
    ): List<PlaceSuggestion> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        return table.withIndex()
            .mapNotNull { (index, place) ->
                place.names.map(::normalize).minOfOrNull { matchRank(it, q) }
                    ?.takeIf { it < NO_MATCH }?.let { Triple(it, index, place) }
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .take(limit)
            .map { it.third.suggestion }
    }

    private fun matchRank(name: String, q: String): Int = when {
        name == q -> 0
        name.startsWith(q) -> 1
        name.contains(" $q") -> 2
        else -> NO_MATCH
    }

    private const val NO_MATCH = 3
    const val MAX_MATCHES = 5
}
