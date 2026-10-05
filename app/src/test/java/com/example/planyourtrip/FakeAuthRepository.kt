package com.example.planyourtrip

import com.example.planyourtrip.data.auth.AuthError
import com.example.planyourtrip.data.auth.AuthException
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.auth.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [AuthRepository] for unit tests. Records calls; set `next*Error` to make the
 * next call of that kind fail. Successful sign in / sign up sign the user in, like Firebase.
 */
class FakeAuthRepository(initialUser: AuthUser? = null) : AuthRepository {
    override val currentUser = MutableStateFlow(initialUser)

    val calls = mutableListOf<String>()
    var nextSignInError: AuthError? = null
    var nextSignUpError: AuthError? = null
    var nextResetError: AuthError? = null

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        calls += "signIn:$email"
        return respond(nextSignInError.also { nextSignInError = null }) {
            currentUser.value = AuthUser(uid = "uid-$email", displayName = null, email = email)
        }
    }

    override suspend fun signUp(fullName: String, email: String, password: String): Result<Unit> {
        calls += "signUp:$fullName:$email"
        return respond(nextSignUpError.also { nextSignUpError = null }) {
            currentUser.value = AuthUser(uid = "uid-$email", displayName = fullName, email = email)
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        calls += "reset:$email"
        return respond(nextResetError.also { nextResetError = null }) {}
    }

    override fun signOut() {
        calls += "signOut"
        currentUser.value = null
    }

    private fun respond(error: AuthError?, onSuccess: () -> Unit): Result<Unit> =
        if (error != null) Result.failure(AuthException(error)) else Result.success(onSuccess())
}
