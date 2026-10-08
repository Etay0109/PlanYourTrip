package com.example.planyourtrip.data.trip

/**
 * Avatar color of a traveler ("You" always uses the brand teal). Assigned automatically, round-robin by the number of
 * travelers the trip already has. Persisted by [name], so entries may be reordered or appended, but never renamed or
 * removed without a migration (unknown names fall back to the first). Values live in `ExtendedColors.travelers`.
 */
enum class TravelerColor {
    ROSE, INDIGO, PURPLE, SKY, GREEN, GREEN_TEAL;

    companion object {
        fun forIndex(index: Int): TravelerColor = entries[Math.floorMod(index, entries.size)]

        fun fromName(name: String?): TravelerColor = entries.firstOrNull { it.name == name } ?: entries.first()
    }
}
