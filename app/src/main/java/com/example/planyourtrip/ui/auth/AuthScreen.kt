package com.example.planyourtrip.ui.auth

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.auth.AuthError
import com.example.planyourtrip.ui.common.components.BrandMark
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.theme.PlanYourTripTheme

/** Stateful entry point: wires [AuthViewModel] to the stateless [AuthContent]. */
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onAuthenticated()
    }
    AuthContent(
        state = state,
        onModeToggle = viewModel::onModeToggle,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmChange = viewModel::onConfirmChange,
        onSubmit = viewModel::onSubmit,
        onForgotPassword = viewModel::onForgotPassword,
        onMessageShown = viewModel::onMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthContent(
    state: AuthUiState,
    onModeToggle: () -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val isSignUp = state.mode == AuthMode.SIGN_UP
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AuthHeader(isSignUp = isSignUp)

                Spacer(Modifier.height(16.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    AuthMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.mode == mode,
                            onClick = { if (state.mode != mode) onModeToggle() },
                            shape = SegmentedButtonDefaults.itemShape(index, AuthMode.entries.size),
                        ) {
                            Text(
                                stringResource(
                                    if (mode == AuthMode.SIGN_IN) R.string.auth_tab_sign_in
                                    else R.string.auth_tab_sign_up
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                AnimatedVisibility(visible = isSignUp) {
                    OutlinedTextField(
                        value = state.fullName,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(R.string.auth_full_name)) },
                        singleLine = true,
                        isError = state.nameError != null,
                        supportingText = errorSupportingText(state.nameError?.messageRes()),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.auth_email)) },
                    singleLine = true,
                    isError = state.emailError != null,
                    supportingText = errorSupportingText(state.emailError?.messageRes()),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )

                PasswordField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = R.string.auth_password,
                    error = state.passwordError,
                    imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done,
                    onDone = onSubmit,
                )

                AnimatedVisibility(visible = isSignUp) {
                    PasswordField(
                        value = state.confirmPassword,
                        onValueChange = onConfirmChange,
                        label = R.string.auth_confirm_password,
                        error = state.confirmError,
                        imeAction = ImeAction.Done,
                        onDone = onSubmit,
                    )
                }

                AnimatedVisibility(
                    visible = !isSignUp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(contentAlignment = Alignment.CenterEnd) {
                        TextButton(onClick = onForgotPassword, enabled = !state.isBusy) {
                            Text(stringResource(R.string.auth_forgot_password))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                PrimaryButton(
                    text = stringResource(
                        if (isSignUp) R.string.auth_button_sign_up else R.string.auth_button_sign_in
                    ),
                    onClick = onSubmit,
                    loading = state.isLoading,
                    enabled = !state.isSendingReset,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AuthHeader(isSignUp: Boolean) {
    BrandMark(size = 88.dp, iconSize = 42.dp)
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onBackground,
    )
    Text(
        text = stringResource(
            if (isSignUp) R.string.auth_subtitle_sign_up else R.string.auth_subtitle_sign_in
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    error: FieldError?,
    imeAction: ImeAction,
    onDone: () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        isError = error != null,
        supportingText = errorSupportingText(error?.messageRes()),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter = painterResource(
                        if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                    ),
                    contentDescription = stringResource(
                        if (visible) R.string.auth_hide_password else R.string.auth_show_password
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
}

@StringRes
private fun FieldError.messageRes(): Int = when (this) {
    FieldError.EMPTY_NAME -> R.string.error_empty_name
    FieldError.EMPTY_EMAIL -> R.string.error_empty_email
    FieldError.INVALID_EMAIL -> R.string.error_invalid_email
    FieldError.EMPTY_PASSWORD -> R.string.error_empty_password
    FieldError.PASSWORD_TOO_SHORT -> R.string.error_password_too_short
    FieldError.PASSWORDS_DONT_MATCH -> R.string.error_passwords_dont_match
    FieldError.WRONG_CREDENTIALS -> R.string.error_wrong_credentials
    FieldError.EMAIL_IN_USE -> R.string.error_email_in_use
    FieldError.WEAK_PASSWORD -> R.string.error_weak_password
}

@Composable
private fun messageText(message: AuthMessage): String = when (message) {
    is AuthMessage.ResetEmailSent -> stringResource(R.string.auth_reset_email_sent, message.email)
    is AuthMessage.Failure -> stringResource(
        when (message.error) {
            AuthError.NO_CONNECTION -> R.string.error_no_connection
            AuthError.TOO_MANY_REQUESTS -> R.string.error_too_many_requests
            // Field-level errors are shown under their field; this only covers the fallback.
            AuthError.INVALID_CREDENTIALS -> R.string.error_wrong_credentials
            AuthError.EMAIL_IN_USE -> R.string.error_email_in_use
            AuthError.WEAK_PASSWORD -> R.string.error_weak_password
            AuthError.UNKNOWN -> R.string.error_unknown
        }
    )
}

@Preview(showBackground = true, name = "Sign In")
@Composable
private fun AuthSignInPreview() {
    PlanYourTripTheme {
        AuthContent(
            state = AuthUiState(email = "traveler@example", emailError = FieldError.INVALID_EMAIL),
            onModeToggle = {}, onNameChange = {}, onEmailChange = {}, onPasswordChange = {},
            onConfirmChange = {}, onSubmit = {}, onForgotPassword = {}, onMessageShown = {},
        )
    }
}

@Preview(showBackground = true, name = "Sign Up")
@Composable
private fun AuthSignUpPreview() {
    PlanYourTripTheme {
        AuthContent(
            state = AuthUiState(
                mode = AuthMode.SIGN_UP,
                password = "short",
                passwordError = FieldError.PASSWORD_TOO_SHORT,
            ),
            onModeToggle = {}, onNameChange = {}, onEmailChange = {}, onPasswordChange = {},
            onConfirmChange = {}, onSubmit = {}, onForgotPassword = {}, onMessageShown = {},
        )
    }
}
