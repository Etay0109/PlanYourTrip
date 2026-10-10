package com.example.planyourtrip.data.sharing

import java.security.SecureRandom
import java.time.Duration
import java.util.Locale
import java.util.Random

/**
 * Invite codes: 8 characters from an alphabet without look-alikes (no 0/O, 1/I/L), 31^8 ≈ 8.5·10^11 codes, made
 * with a cryptographically secure random source. firestore.rules accept exactly this format. Pure.
 */
object InviteCode {
    const val LENGTH = 8
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    /** A code works for 7 days from its (server) creation time; firestore.rules check the same. */
    val VALIDITY: Duration = Duration.ofDays(7)

    private val secureRandom by lazy { SecureRandom() }

    /** A new random code. Tests pass a seeded [random]; the app always uses [SecureRandom]. */
    fun generate(random: Random = secureRandom): String =
        buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    /** Typed text as a code: upper case, only alphabet characters (spaces, dashes and look-alikes dropped), ≤ 8. */
    fun normalize(text: String): String = text.uppercase(Locale.ROOT).filter { it in ALPHABET }.take(LENGTH)

    /**
     * The code inside pasted text, such as the shared message ("… with the code K7M4-XP2R …"), "k7m4 xp2r" or
     * "K7M4XP2R"; null when there is none. Only a group of exactly 8 code characters (optionally split 4 + 4 by a
     * space or dash) counts, so ordinary words around it are never taken as a code.
     */
    fun extract(text: String): String? {
        val upper = text.uppercase(Locale.ROOT)
        val match = PASTED.find(upper) ?: return null
        return match.groupValues[1] + match.groupValues[2]
    }

    /**
     * The field's new text after the user typed or pasted [new] over [old]: a paste of several characters keeps the
     * code inside it ([extract]); otherwise the characters are [normalize]d.
     */
    fun input(old: String, new: String): String =
        if (new.length - old.length > 1) extract(new) ?: normalize(new) else normalize(new)

    /** "K7M4 XP2R": two groups of four. */
    fun display(code: String): String = if (code.length == LENGTH) "${code.take(4)} ${code.drop(4)}" else code

    /** "K7M4-XP2R": the form used in the shared message (found again by [extract]). */
    fun forMessage(code: String): String = if (code.length == LENGTH) "${code.take(4)}-${code.drop(4)}" else code

    fun expiresAt(createdAtMillis: Long): Long = createdAtMillis + VALIDITY.toMillis()

    private val PASTED = Regex("(?<![A-Z0-9])([$ALPHABET]{4})[ -]?([$ALPHABET]{4})(?![A-Z0-9])")
}
