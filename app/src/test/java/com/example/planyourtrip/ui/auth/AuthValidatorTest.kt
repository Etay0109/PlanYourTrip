package com.example.planyourtrip.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun validEmails_pass() {
        listOf("a@b.co", "first.last+tag@mail.example.com", " user@site.org ").forEach {
            assertNull(it, AuthValidator.validateEmail(it))
        }
    }

    @Test
    fun invalidEmails_fail() {
        listOf("plain", "a@b", "@b.com", "a@.com", "a b@c.com", "a@b.c").forEach {
            assertEquals(it, FieldError.INVALID_EMAIL, AuthValidator.validateEmail(it))
        }
    }

    @Test
    fun blankEmail_isEmpty() {
        assertEquals(FieldError.EMPTY_EMAIL, AuthValidator.validateEmail("  "))
    }

    @Test
    fun newPassword_requiresEightChars() {
        assertEquals(FieldError.EMPTY_PASSWORD, AuthValidator.validateNewPassword(""))
        assertEquals(FieldError.PASSWORD_TOO_SHORT, AuthValidator.validateNewPassword("1234567"))
        assertNull(AuthValidator.validateNewPassword("12345678"))
    }

    @Test
    fun existingPassword_onlyRequiresNonEmpty() {
        assertEquals(FieldError.EMPTY_PASSWORD, AuthValidator.validateExistingPassword(""))
        assertNull(AuthValidator.validateExistingPassword("123"))
    }

    @Test
    fun confirm_mustMatch() {
        assertNull(AuthValidator.validateConfirm("password1", "password1"))
        assertEquals(FieldError.PASSWORDS_DONT_MATCH, AuthValidator.validateConfirm("password1", "password2"))
    }

    @Test
    fun blankName_isEmpty() {
        assertEquals(FieldError.EMPTY_NAME, AuthValidator.validateName(" "))
        assertNull(AuthValidator.validateName("Dana Levi"))
    }

    @Test
    fun confirm_bothEmpty_isEmptyConfirm_otherCasesUnchanged() {
        assertEquals(FieldError.EMPTY_CONFIRM, AuthValidator.validateConfirm("", ""))
        // An empty confirmation of a typed password keeps the old message.
        assertEquals(FieldError.PASSWORDS_DONT_MATCH, AuthValidator.validateConfirm("password1", ""))
        assertEquals(FieldError.PASSWORDS_DONT_MATCH, AuthValidator.validateConfirm("", "x"))
    }

    @Test
    fun strength_followsTheDisplayRule() {
        assertEquals(0, AuthValidator.passwordStrength(""))
        assertEquals(1, AuthValidator.passwordStrength("a"))
        assertEquals(1, AuthValidator.passwordStrength("abc123!")) // 7 characters, everything else doesn't count
        assertEquals(2, AuthValidator.passwordStrength("abcdefgh"))
        assertEquals(2, AuthValidator.passwordStrength("12345678"))
        assertEquals(2, AuthValidator.passwordStrength("!!!!!!!!"))
        assertEquals(3, AuthValidator.passwordStrength("abcd1234"))
        assertEquals(3, AuthValidator.passwordStrength("abcd1234!")) // symbol, but under 12
        assertEquals(3, AuthValidator.passwordStrength("abcdefgh1234")) // 12, but no symbol
        assertEquals(2, AuthValidator.passwordStrength("abcdefghijk!")) // 12 + symbol, but no digit
        assertEquals(4, AuthValidator.passwordStrength("abcdefgh123!"))
        assertEquals(3, AuthValidator.passwordStrength("abcd efgh 12")) // a space isn't a symbol
    }

    @Test
    fun strength_neverChangesWhatSignUpAccepts() {
        // Weak on the bar, still accepted; strong-looking but short, still rejected.
        assertNull(AuthValidator.validateNewPassword("aaaaaaaa"))
        assertEquals(2, AuthValidator.passwordStrength("aaaaaaaa"))
        assertEquals(FieldError.PASSWORD_TOO_SHORT, AuthValidator.validateNewPassword("a1!b2@c"))
        assertEquals(1, AuthValidator.passwordStrength("a1!b2@c"))
    }
}
