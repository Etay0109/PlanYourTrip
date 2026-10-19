package com.example.planyourtrip.ui.auth

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** The form's fields, top to bottom (Sign In shows only [EMAIL] and [PASSWORD]). */
internal enum class AuthField { NAME, EMAIL, PASSWORD, CONFIRM }

/** Where the sheet, the fields and the button are, so the focused field and the button can be scrolled above the keyboard. */
internal class AuthLayout {
    var viewportHeight = 0
    var sheet: LayoutCoordinates? = null
    var button: LayoutCoordinates? = null
    val fields = mutableMapOf<AuthField, LayoutCoordinates>()
}

/** Focus and keyboard scrolling of the form's fields; see [rememberAuthFormFocus]. */
@OptIn(ExperimentalFoundationApi::class)
internal class AuthFormFocus {
    val requesters = AuthField.entries.associateWith { FocusRequester() }
    val layout = AuthLayout()
    val sheetRequester = BringIntoViewRequester()
    var focusedField by mutableStateOf<AuthField?>(null)

    /** Makes this the [field]'s input: focus requests, the focused field, and its place for keyboard scrolling. */
    fun Modifier.authField(field: AuthField): Modifier = this
        .focusRequester(requesters.getValue(field))
        .onFocusChanged {
            if (it.isFocused) focusedField = field else if (focusedField == field) focusedField = null
        }
        .onGloballyPositioned { layout.fields[field] = it }
}

/**
 * A submit stopped by validation focuses the first field with an error; a focused field (and, if it fits, the button)
 * is scrolled above the keyboard.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberAuthFormFocus(state: AuthUiState): AuthFormFocus {
    val focus = remember { AuthFormFocus() }
    // A submit stopped by validation moves focus to the first field that needs attention.
    LaunchedEffect(state.failedSubmits) {
        if (state.failedSubmits == 0) return@LaunchedEffect
        firstFieldWithError(state)?.let { focus.requesters.getValue(it).requestFocus() }
    }

    val ime = WindowInsets.ime
    val density = LocalDensity.current
    // Whenever a field gets focus or the keyboard moves, scroll so the field and (if it fits) the button are visible.
    LaunchedEffect(focus.focusedField) {
        val field = focus.focusedField ?: return@LaunchedEffect
        snapshotFlow { ime.getBottom(density) }.collectLatest {
            delay(KEYBOARD_SETTLE_MILLIS)
            val sheet = focus.layout.sheet?.takeIf { it.isAttached } ?: return@collectLatest
            val fieldBox = focus.layout.fields[field]?.takeIf { it.isAttached }?.let { sheet.localBoundingBoxOf(it) }
                ?: return@collectLatest
            val buttonBottom = focus.layout.button?.takeIf { it.isAttached }?.let { sheet.localBoundingBoxOf(it).bottom }
                ?: fieldBox.bottom
            val bottom = minOf(buttonBottom, fieldBox.top + focus.layout.viewportHeight)
            focus.sheetRequester.bringIntoView(Rect(0f, fieldBox.top, sheet.size.width.toFloat(), bottom))
        }
    }
    return focus
}

/**
 * The sheet's content: the fields (name and confirmation only for Sign Up), the button and the other mode's link.
 * The typed text is Compose state here, kept when the mode changes; the name and email survive process death
 * ([rememberSaveable]), the passwords are never saved.
 */
@Composable
internal fun AuthForm(
    state: AuthUiState,
    onModeToggle: () -> Unit,
    onNameEdited: () -> Unit,
    onEmailEdited: () -> Unit,
    onPasswordEdited: () -> Unit,
    onConfirmEdited: () -> Unit,
    onSubmit: (AuthInput) -> Unit,
    focus: AuthFormFocus,
    initialInput: AuthInput = AuthInput(),
) {
    var fullName by rememberSaveable { mutableStateOf(initialInput.fullName) }
    var email by rememberSaveable { mutableStateOf(initialInput.email) }
    var password by remember { mutableStateOf(initialInput.password) }
    var confirmPassword by remember { mutableStateOf(initialInput.confirmPassword) }
    val submit = { onSubmit(AuthInput(fullName, email, password, confirmPassword)) }
    val isSignUp = state.mode == AuthMode.SIGN_UP
    val focusManager = LocalFocusManager.current
    fun Modifier.field(field: AuthField): Modifier = with(focus) { authField(field) }

    Column(
        modifier = Modifier.widthIn(max = FormMaxWidth).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.mPlus),
    ) {
        if (isSignUp) {
            AuthTextField(
                value = fullName,
                onValueChange = { fullName = it; onNameEdited() },
                label = R.string.auth_full_name,
                icon = R.drawable.ic_person_outline,
                error = state.nameError,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.field(AuthField.NAME),
            )
        }
        AuthTextField(
            value = email,
            onValueChange = { email = it; onEmailEdited() },
            label = R.string.auth_email,
            icon = R.drawable.ic_mail_outline,
            error = state.emailError,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.field(AuthField.EMAIL),
        )
        AuthTextField(
            value = password,
            onValueChange = { password = it; onPasswordEdited() },
            label = R.string.auth_password,
            icon = R.drawable.ic_lock_outline,
            error = state.passwordError,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            isPassword = true,
            strength = if (isSignUp) AuthValidator.passwordStrength(password) else null,
            hint = if (isSignUp) stringResource(R.string.auth_password_hint) else null,
            modifier = Modifier.field(AuthField.PASSWORD),
        )
        if (isSignUp) {
            AuthTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; onConfirmEdited() },
                label = R.string.auth_confirm_password,
                icon = R.drawable.ic_lock_outline,
                error = state.confirmError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                isPassword = true,
                modifier = Modifier.field(AuthField.CONFIRM),
            )
        }

        Spacer(Modifier.height(Spacing.xsPlus))
        AuthFormButtons(
            isSignUp = isSignUp,
            loading = state.isLoading,
            onSubmit = submit,
            onModeToggle = {
                focusManager.clearFocus()
                onModeToggle()
            },
            focus = focus,
        )
    }
}

/** "Sign in" / "Create account", then the line that switches to the other mode. */
@Composable
private fun AuthFormButtons(
    isSignUp: Boolean,
    loading: Boolean,
    onSubmit: () -> Unit,
    onModeToggle: () -> Unit,
    focus: AuthFormFocus,
) {
    AuthButton(
        text = stringResource(
            if (isSignUp) R.string.auth_button_sign_up else R.string.auth_button_sign_in
        ),
        loading = loading,
        onClick = onSubmit,
        modifier = Modifier.onGloballyPositioned { focus.layout.button = it },
    )
    Spacer(Modifier.height(Spacing.xs))
    SwitchModeLine(isSignUp = isSignUp, onClick = onModeToggle)
}

/** Long enough for the keyboard's resize to finish before scrolling the focused field into view. */
private const val KEYBOARD_SETTLE_MILLIS = 120L

private fun firstFieldWithError(state: AuthUiState): AuthField? {
    val isSignUp = state.mode == AuthMode.SIGN_UP
    return when {
        isSignUp && state.nameError != null -> AuthField.NAME
        state.emailError != null -> AuthField.EMAIL
        state.passwordError != null -> AuthField.PASSWORD
        isSignUp && state.confirmError != null -> AuthField.CONFIRM
        else -> null
    }
}

/** On wide screens (tablets, landscape) the form stops growing at this width. */
private val FormMaxWidth = 480.dp
