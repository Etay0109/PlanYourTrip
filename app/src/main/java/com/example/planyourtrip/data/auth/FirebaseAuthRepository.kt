package com.example.planyourtrip.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Firebase Authentication (email/password). The only class in the app that imports Firebase Auth.
 * One instance per process (see AppContainer), so its auth-state listener lives as long as the app.
 */
class FirebaseAuthRepository(private val auth: FirebaseAuth) : AuthRepository {

    private val _currentUser = MutableStateFlow(auth.currentUser?.toAuthUser())
    override val currentUser: StateFlow<AuthUser?> = _currentUser.asStateFlow()

    init {
        // Fires on sign in and sign out (not on profile changes; signUp publishes those itself).
        auth.addAuthStateListener { _currentUser.value = it.currentUser?.toAuthUser() }
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = request {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    override suspend fun signUp(fullName: String, email: String, password: String): Result<Unit> = request {
        val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
        // The account exists at this point, so a failed name update does not fail sign up;
        // the greeting then falls back to the email prefix.
        val nameSaved = runCatching {
            user.updateProfile(userProfileChangeRequest { displayName = fullName }).await()
        }.onFailure { if (it is CancellationException) throw it }.isSuccess
        val signedIn = user.toAuthUser()
        _currentUser.value = if (nameSaved) signedIn.copy(displayName = fullName) else signedIn
    }

    override fun signOut() = auth.signOut()

    /** Runs a Firebase call and converts its failure into an [AuthException]. */
    private suspend fun request(block: suspend () -> Unit): Result<Unit> =
        try {
            block()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(AuthException(e.toAuthError(), e))
        }

    private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, displayName = displayName, email = email)

    private fun Exception.toAuthError(): AuthError = when (this) {
        // With email-enumeration protection Firebase reports a wrong email and a wrong password the same way.
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD // subclass of InvalidCredentials: check first
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> AuthError.INVALID_CREDENTIALS
        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_IN_USE
        is FirebaseNetworkException -> AuthError.NO_CONNECTION
        is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_REQUESTS
        else -> AuthError.UNKNOWN
    }
}
