package com.example.planyourtrip.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
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

class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
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
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(fullName = value, nameError = null) }

    fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, emailError = null) }

    fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, passwordError = null) }

    fun onConfirmChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value, confirmError = null) }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun onSubmit() {
        val state = _uiState.value
        if (state.isBusy) return

        val validated = when (state.mode) {
            AuthMode.SIGN_IN -> state.copy(
                emailError = AuthValidator.validateEmail(state.email),
                passwordError = AuthValidator.validateExistingPassword(state.password),
            )
            AuthMode.SIGN_UP -> state.copy(
                nameError = AuthValidator.validateName(state.fullName),
                emailError = AuthValidator.validateEmail(state.email),
                passwordError = AuthValidator.validateNewPassword(state.password),
                confirmError = AuthValidator.validateConfirm(state.password, state.confirmPassword),
            )
        }
        val hasErrors = listOf(
            validated.nameError, validated.emailError, validated.passwordError, validated.confirmError,
        ).any { it != null }
        if (hasErrors) {
            _uiState.value = validated
            return
        }

        // Set synchronously so a second tap in the same frame is ignored.
        _uiState.value = validated.copy(isLoading = true, message = null)
        val email = validated.email.trim()
        viewModelScope.launch {
            val result = when (validated.mode) {
                AuthMode.SIGN_IN -> repository.signIn(email, validated.password)
                AuthMode.SIGN_UP -> repository.signUp(validated.fullName.trim(), email, validated.password)
            }
            _uiState.update { current ->
                result.fold(
                    onSuccess = { current.copy(isLoading = false, isAuthenticated = true) },
                    onFailure = { current.copy(isLoading = false).withError(it.authError()) },
                )
            }
        }
    }

    fun onForgotPassword() {
        val state = _uiState.value
        if (state.isBusy) return
        val error = AuthValidator.validateEmail(state.email)
        if (error != null) {
            _uiState.update { it.copy(emailError = error) }
            return
        }

        val email = state.email.trim()
        _uiState.update { it.copy(isSendingReset = true, message = null) }
        viewModelScope.launch {
            val result = repository.sendPasswordReset(email)
            _uiState.update { current ->
                current.copy(
                    isSendingReset = false,
                    message = result.fold(
                        onSuccess = { AuthMessage.ResetEmailSent(email) },
                        onFailure = { AuthMessage.Failure(it.authError()) },
                    ),
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(appContainer().authRepository) }
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
