package com.example.planyourtrip.ui.auth

/** Field error codes (client validation and backend rejections). Mapped to string resources in the UI. */
enum class FieldError {
    EMPTY_NAME,
    EMPTY_EMAIL,
    INVALID_EMAIL,
    EMPTY_PASSWORD,
    PASSWORD_TOO_SHORT,
    PASSWORDS_DONT_MATCH,

    // Returned by the auth backend
    WRONG_CREDENTIALS,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
}

/**
 * Pure-Kotlin validation rules (no Android dependencies, so it runs in JVM unit tests).
 * Each function returns null when the value is valid.
 */
object AuthValidator {

    const val MIN_PASSWORD_LENGTH = 8

    // android.util.Patterns is unavailable in local unit tests, so we use our own regex.
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    fun validateName(name: String): FieldError? =
        if (name.isBlank()) FieldError.EMPTY_NAME else null

    fun validateEmail(email: String): FieldError? = when {
        email.isBlank() -> FieldError.EMPTY_EMAIL
        !EMAIL_REGEX.matches(email.trim()) -> FieldError.INVALID_EMAIL
        else -> null
    }

    /** Sign Up rule: password must be non-empty and at least [MIN_PASSWORD_LENGTH] characters. */
    fun validateNewPassword(password: String): FieldError? = when {
        password.isEmpty() -> FieldError.EMPTY_PASSWORD
        password.length < MIN_PASSWORD_LENGTH -> FieldError.PASSWORD_TOO_SHORT
        else -> null
    }

    /** Sign In rule: only require a password; the backend decides if it is correct. */
    fun validateExistingPassword(password: String): FieldError? =
        if (password.isEmpty()) FieldError.EMPTY_PASSWORD else null

    fun validateConfirm(password: String, confirm: String): FieldError? =
        if (password != confirm) FieldError.PASSWORDS_DONT_MATCH else null
}
