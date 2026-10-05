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
}
