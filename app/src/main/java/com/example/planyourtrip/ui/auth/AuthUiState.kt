package com.example.planyourtrip.ui.auth

import com.example.planyourtrip.data.auth.AuthError

enum class AuthMode { SIGN_IN, SIGN_UP }

/** One-time message shown in a snackbar, then cleared with `AuthViewModel.onMessageShown()`. */
sealed interface AuthMessage {
    /** A failure that does not belong to a single field (connection, rate limit, unknown). */
    data class Failure(val error: AuthError) : AuthMessage
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val nameError: FieldError? = null,
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val confirmError: FieldError? = null,
    /** Counts submits stopped by validation; each new value moves focus to the first field with an error. */
    val failedSubmits: Int = 0,
    /** A sign in / sign up request is running. */
    val isLoading: Boolean = false,
    val message: AuthMessage? = null,
    /** Set after a successful sign in / sign up; the screen then navigates to Home. */
    val isAuthenticated: Boolean = false,
)
