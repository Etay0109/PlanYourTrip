package com.example.planyourtrip.ui.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.planyourtrip.ui.theme.PlanYourTripTheme

@Composable
private fun AuthPreview(state: AuthUiState, darkTheme: Boolean = false) {
    PlanYourTripTheme(darkTheme = darkTheme) {
        AuthContent(
            state = state,
            onModeToggle = {}, onNameChange = {}, onEmailChange = {}, onPasswordChange = {},
            onConfirmChange = {}, onSubmit = {}, onMessageShown = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915, name = "Sign In")
@Composable
internal fun AuthSignInPreview() {
    AuthPreview(AuthUiState(email = "traveler@example", emailError = FieldError.INVALID_EMAIL))
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915, name = "Sign Up")
@Composable
internal fun AuthSignUpPreview() {
    AuthPreview(AuthUiState(mode = AuthMode.SIGN_UP, fullName = "Alex Morgan", password = "travel2026"))
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915, name = "Sign Up, dark")
@Composable
internal fun AuthSignUpDarkPreview() {
    AuthPreview(
        AuthUiState(mode = AuthMode.SIGN_UP, password = "short", passwordError = FieldError.PASSWORD_TOO_SHORT),
        darkTheme = true,
    )
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640, fontScale = 1.5f, name = "Sign Up, 320dp x1.5")
@Composable
internal fun AuthSignUpSmallPreview() {
    AuthPreview(
        AuthUiState(
            mode = AuthMode.SIGN_UP,
            nameError = FieldError.EMPTY_NAME,
            emailError = FieldError.EMPTY_EMAIL,
            passwordError = FieldError.EMPTY_PASSWORD,
            confirmError = FieldError.EMPTY_CONFIRM,
        ),
    )
}
