package com.example.planyourtrip.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthError
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.auth.authError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The typed text stays in the form ([AuthInput]); this keeps the mode (also across process death, in [savedState]),
 * the field errors and the request.
 */
class AuthViewModel(
    private val repository: AuthRepository,
    /** Keeps the mode across process death; null (tests) keeps nothing. */
    private val savedState: SavedStateHandle? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(mode = savedState?.get<AuthMode>(KEY_MODE) ?: AuthMode.SIGN_IN))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onModeToggle() {
        _uiState.update {
            it.copy(
                mode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
                nameError = null,
                emailError = null,
                passwordError = null,
                confirmError = null,
            )
        }
        savedState?.set(KEY_MODE, _uiState.value.mode)
    }

    // Typing in a field clears its error.
    fun onNameEdited() = _uiState.update { it.copy(nameError = null) }

    fun onEmailEdited() = _uiState.update { it.copy(emailError = null) }

    fun onPasswordEdited() = _uiState.update { it.copy(passwordError = null) }

    fun onConfirmEdited() = _uiState.update { it.copy(confirmError = null) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSubmit(input: AuthInput) {
        val state = _uiState.value
        if (state.isLoading) return

        val validated = when (state.mode) {
            AuthMode.SIGN_IN -> state.copy(
                emailError = AuthValidator.validateEmail(input.email),
                passwordError = AuthValidator.validateExistingPassword(input.password),
            )
            AuthMode.SIGN_UP -> state.copy(
                nameError = AuthValidator.validateName(input.fullName),
                emailError = AuthValidator.validateEmail(input.email),
                passwordError = AuthValidator.validateNewPassword(input.password),
                confirmError = AuthValidator.validateConfirm(input.password, input.confirmPassword),
            )
        }
        val hasErrors = listOf(
            validated.nameError, validated.emailError, validated.passwordError, validated.confirmError,
        ).any { it != null }
        if (hasErrors) {
            _uiState.value = validated.copy(failedSubmits = state.failedSubmits + 1)
            return
        }

        // Set synchronously so a second tap in the same frame is ignored.
        _uiState.value = validated.copy(isLoading = true, message = null)
        val email = input.email.trim()
        viewModelScope.launch {
            val result = when (validated.mode) {
                AuthMode.SIGN_IN -> repository.signIn(email, input.password)
                AuthMode.SIGN_UP -> repository.signUp(input.fullName.trim(), email, input.password)
            }
            _uiState.update { current ->
                result.fold(
                    onSuccess = { current.copy(isLoading = false, isAuthenticated = true) },
                    onFailure = { current.copy(isLoading = false).withError(it.authError()) },
                )
            }
        }
    }

    companion object {
        private const val KEY_MODE = "mode"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(appContainer().authRepository, createSavedStateHandle()) }
        }
    }
}

/** Shows a backend error under the field it concerns, or as a snackbar when it concerns no single field. */
private fun AuthUiState.withError(error: AuthError): AuthUiState = when (error) {
    AuthError.INVALID_CREDENTIALS -> copy(passwordError = FieldError.WRONG_CREDENTIALS)
    AuthError.EMAIL_IN_USE -> copy(emailError = FieldError.EMAIL_IN_USE)
    AuthError.WEAK_PASSWORD -> copy(passwordError = FieldError.WEAK_PASSWORD)
    AuthError.NO_CONNECTION, AuthError.TOO_MANY_REQUESTS, AuthError.UNKNOWN ->
        copy(message = AuthMessage.Failure(error))
}
