package com.example.planyourtrip.data.auth

/** Backend-independent reasons an auth request can fail. Mapped to user-facing messages in the UI. */
enum class AuthError {
    /** Wrong email or password (the backend does not reveal which). */
    INVALID_CREDENTIALS,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    NO_CONNECTION,
    TOO_MANY_REQUESTS,
    UNKNOWN,
}

/** Failure carried in `Result.failure(...)` by [AuthRepository] calls. */
class AuthException(val error: AuthError, cause: Throwable? = null) : Exception(error.name, cause)

/** The [AuthError] of a failed [AuthRepository] result; [AuthError.UNKNOWN] for any other exception. */
fun Throwable.authError(): AuthError = (this as? AuthException)?.error ?: AuthError.UNKNOWN
