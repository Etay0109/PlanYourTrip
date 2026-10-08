package com.example.planyourtrip.data.trip

/**
 * Pastel color of a trip's letter square. Persisted by [name], so entries may be
 * reordered or appended freely, but never renamed or removed without a migration.
 * Actual color values live in the theme (`ExtendedColors.pastels`).
 */
enum class TripColor {
    SKY, PEACH, MINT, LAVENDER, ROSE, LEMON;

    companion object {
        /** Round-robin pick: the n-th trip created gets the n-th palette color. */
        fun forIndex(index: Int): TripColor = entries[Math.floorMod(index, entries.size)]

        /** Unknown names (e.g. a removed palette entry) fall back to the first color instead of crashing. */
        fun fromName(name: String?): TripColor = entries.firstOrNull { it.name == name } ?: entries.first()
    }
}
