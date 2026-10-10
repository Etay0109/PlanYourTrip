package com.example.planyourtrip.ui.common.split

import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.TripMember

/** How a traveler key is shown: "You", another member, a named traveler, or a traveler that no longer exists. */
sealed interface Person {
    val key: String

    /** The signed-in member; [key] is their account uid. */
    data class You(override val key: String) : Person

    /**
     * Another member of the shared trip ([key] = their uid), or one who left or was removed ([left]) but still
     * appears in expenses or settlements. [name] is null until their member document has been downloaded.
     * Never renamed or removed from the Budget tab.
     */
    data class Member(
        override val key: String,
        val name: String?,
        val color: TravelerColor,
        val isOwner: Boolean = false,
        val left: Boolean = false,
    ) : Person

    /**
     * A name-only traveler (not an account). [nameOnly] = their name matches a member of the trip ("Noa" and the
     * member "Noa Levi"), so they are labeled "name only" until merged with that member (see TravelerMerging.kt).
     */
    data class Named(
        override val key: String,
        val name: String,
        val color: TravelerColor,
        val nameOnly: Boolean = false,
    ) : Person

    /** A key no traveler or member has any more (removed on another device while this one still used it). */
    data class Removed(override val key: String) : Person
}

/**
 * "You" ([me]), the trip's other current members ([memberIds] order, names from [members]) and its travelers, in
 * list order.
 */
fun people(
    me: String,
    travelers: List<Traveler>,
    memberIds: List<String> = emptyList(),
    members: List<TripMember> = emptyList(),
): List<Person> =
    listOf(Person.You(me)) +
        memberIds.filter { it != me }.map { memberOf(it, memberIds, members) } +
        travelers.map { named(it, memberIds, members) }

/**
 * The person behind [key] ([me] = "You"): a current member, a traveler, a former member (their member document
 * says they left), or [Person.Removed].
 */
fun personOf(
    key: String,
    me: String,
    travelers: List<Traveler>,
    memberIds: List<String> = emptyList(),
    members: List<TripMember> = emptyList(),
): Person = when {
    key == me -> Person.You(me)
    key in memberIds -> memberOf(key, memberIds, members)
    else -> travelers.firstOrNull { it.uuid == key }?.let { named(it, memberIds, members) }
        ?: members.firstOrNull { it.uid == key }?.let {
            Person.Member(key, it.name, TravelerColor.forIndex(-1), left = true)
        }
        ?: Person.Removed(key)
}

private fun named(traveler: Traveler, memberIds: List<String>, members: List<TripMember>) = Person.Named(
    key = traveler.uuid,
    name = traveler.name,
    color = traveler.color,
    nameOnly = membersNamed(traveler.name, memberIds, members).isNotEmpty(),
)

/**
 * A current member: the color follows their place in [memberIds] (the same on every device; the first member to
 * join gets the first color), the owner is the first of [memberIds].
 */
private fun memberOf(key: String, memberIds: List<String>, members: List<TripMember>): Person.Member {
    val index = memberIds.indexOf(key)
    return Person.Member(
        key = key,
        name = members.firstOrNull { it.uid == key }?.name,
        color = TravelerColor.forIndex(index - 1),
        isOwner = index == 0,
    )
}

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
