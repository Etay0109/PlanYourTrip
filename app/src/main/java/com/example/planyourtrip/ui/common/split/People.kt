package com.example.planyourtrip.ui.common.split

import com.example.planyourtrip.data.trip.ME
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor

/** How a traveler key is shown: "You", a named traveler, or a traveler that no longer exists. */
sealed interface Person {
    val key: String

    data object You : Person {
        override val key: String = ME
    }

    data class Named(override val key: String, val name: String, val color: TravelerColor) : Person

    /** A key no traveler has any more (removed on another device while this one still used it). */
    data class Removed(override val key: String) : Person
}

/** "You" and the trip's travelers, in list order. */
fun people(travelers: List<Traveler>): List<Person> =
    listOf(Person.You) + travelers.map { Person.Named(it.uuid, it.name, it.color) }

/** The person behind [key]; [Person.Removed] when no traveler has it. */
fun personOf(key: String, travelers: List<Traveler>): Person =
    if (key == ME) Person.You
    else travelers.firstOrNull { it.uuid == key }?.let { Person.Named(it.uuid, it.name, it.color) }
        ?: Person.Removed(key)

/** One-letter avatar text of a named traveler ("Noa" → "N"); "?" when the name has no letter or digit. */
fun avatarLetter(name: String): String =
    name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

/** Problems with a traveler's name. */
enum class TravelerNameError { EMPTY, DUPLICATE, RESERVED }

/**
 * Validates a traveler's name: not blank, not "You" (any case), and different from the trip's other travelers
 * (trimmed, ignoring case). [editingKey] is the traveler being renamed (its own name doesn't count).
 */
fun validateTravelerName(name: String, travelers: List<Traveler>, editingKey: String? = null): TravelerNameError? {
    val clean = name.trim()
    if (clean.isEmpty()) return TravelerNameError.EMPTY
    if (clean.equals(YOU_NAME, ignoreCase = true)) return TravelerNameError.RESERVED
    val taken = travelers.any { it.uuid != editingKey && it.name.trim().equals(clean, ignoreCase = true) }
    return if (taken) TravelerNameError.DUPLICATE else null
}

/** The name "You" is shown for the signed-in user, so no traveler may use it. */
private const val YOU_NAME = "You"
