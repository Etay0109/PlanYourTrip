package com.example.planyourtrip.ui.auth

import com.example.planyourtrip.data.auth.AuthError

enum class AuthMode { SIGN_IN, SIGN_UP }

/** One-time message shown in a snackbar, then cleared with `AuthViewModel.onMessageShown()`. */
sealed interface AuthMessage {
    data class ResetEmailSent(val email: String) : AuthMessage

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
    /** A sign in / sign up request is running. */
    val isLoading: Boolean = false,
    /** A password reset request is running. */
    val isSendingReset: Boolean = false,
    val message: AuthMessage? = null,
    /** Set after a successful sign in / sign up; the screen then navigates to Home. */
    val isAuthenticated: Boolean = false,
) {
    /** True while any request runs; buttons are disabled to prevent double taps. */
    val isBusy: Boolean get() = isLoading || isSendingReset
}
