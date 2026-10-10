package com.example.planyourtrip.data.sharing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class InviteCodeTest {

    @Test
    fun alphabet_hasNoLookAlikes() {
        for (c in "01OIL") assertFalse("$c", c in InviteCode.ALPHABET)
        assertEquals(31, InviteCode.ALPHABET.length)
        assertEquals(InviteCode.ALPHABET.length, InviteCode.ALPHABET.toSet().size)
    }

    @Test
    fun generatedCodes_areValid_andUseTheWholeAlphabet() {
        val random = Random(42)
        val codes = List(2_000) { InviteCode.generate(random) }
        codes.forEach { assertTrue(it, InviteCode.isValid(it)) }
        assertEquals(InviteCode.ALPHABET.toSet(), codes.joinToString("").toSet())
        assertTrue(codes.toSet().size > 1_990)
    }

    @Test
    fun theDefaultSource_isSecureRandom_andGivesValidCodes() {
        repeat(20) { assertTrue(InviteCode.isValid(InviteCode.generate())) }
    }

    @Test
    fun isValid_needsExactlyEightAlphabetCharacters() {
        assertTrue(InviteCode.isValid("K7M4XP2R"))
        assertFalse(InviteCode.isValid("K7M4XP2"))
        assertFalse(InviteCode.isValid("K7M4XP2RR"))
        assertFalse(InviteCode.isValid("k7m4xp2r"))
        assertFalse(InviteCode.isValid("K7M4XP20"))
        assertFalse(InviteCode.isValid("K7M4 XP2"))
    }

    @Test
    fun typing_isUpperCased_andDropsAnythingElse() {
        assertEquals("K7M4XP2R", InviteCode.normalize("k7m4-xp2r"))
        assertEquals("K7M4", InviteCode.normalize("k7 m4"))
        assertEquals("K7M4XP2R", InviteCode.normalize("K7M4XP2RABC"))
        assertEquals("", InviteCode.normalize("01OIL"))
    }

    @Test
    fun pasting_findsTheCodeInTheSharedMessage() {
        val message = "Join my trip to Rome, Italy on Plan Your Trip with the code K7M4-XP2R. " +
            "It works once and expires on 16 Oct."
        assertEquals("K7M4XP2R", InviteCode.extract(message))
        assertEquals("K7M4XP2R", InviteCode.extract("k7m4 xp2r"))
        assertEquals("K7M4XP2R", InviteCode.extract("  K7M4XP2R\n"))
        assertNull(InviteCode.extract("no code here"))
        assertNull(InviteCode.extract("K7M4XP2RZ"))
        // A paste into the field keeps only the code; typing one character at a time is normalized.
        assertEquals("K7M4XP2R", InviteCode.input("", message))
        assertEquals("K7M", InviteCode.input("K7", "K7m"))
    }

    @Test
    fun display_andMessage_splitTheCodeIntoTwoGroups() {
        assertEquals("K7M4 XP2R", InviteCode.display("K7M4XP2R"))
        assertEquals("K7M4-XP2R", InviteCode.forMessage("K7M4XP2R"))
    }

    @Test
    fun aCodeExpiresSevenDaysAfterItWasCreated() {
        val created = 1_000_000L
        val invite = Invite("K7M4XP2R", created)
        assertEquals(created + 7 * 24 * 60 * 60 * 1000L, invite.expiresAt)
        assertTrue(invite.isOpenAt(invite.expiresAt - 1))
        assertFalse(invite.isOpenAt(invite.expiresAt))
    }

    @Test
    fun memberName_isTheDisplayName_neverTheEmail() {
        val noName = com.example.planyourtrip.data.auth.AuthUser("u1", null, "noa.levi@example.com")
        assertEquals(MEMBER_FALLBACK_NAME, memberName(noName))
        assertEquals(MEMBER_FALLBACK_NAME, memberName(noName.copy(displayName = "  ")))
        assertEquals("Noa", memberName(noName.copy(displayName = " Noa ")))
        assertEquals(100, memberName(noName.copy(displayName = "x".repeat(150))).length)
    }
}
