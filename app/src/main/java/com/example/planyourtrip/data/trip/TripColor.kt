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
    }
}
