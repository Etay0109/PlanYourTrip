package com.example.planyourtrip.data.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Authentication backend abstraction. ViewModels and screens only talk to this interface,
 * so they never depend on Firebase classes and unit tests can use a fake.
 * Failed calls return `Result.failure(AuthException(...))`.
 */
interface AuthRepository {
    /** The signed-in user, or null when signed out. Restored automatically on app start. */
    val currentUser: StateFlow<AuthUser?>

    suspend fun signIn(email: String, password: String): Result<Unit>

    /** Creates the account, signs it in and saves [fullName] as its display name. */
    suspend fun signUp(fullName: String, email: String, password: String): Result<Unit>

    suspend fun sendPasswordReset(email: String): Result<Unit>

    fun signOut()
}
