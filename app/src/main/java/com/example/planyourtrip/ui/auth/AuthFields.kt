package com.example.planyourtrip.ui.auth

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.auth.AuthError
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing

/**
 * A 56dp field with 16dp corners: a light fill and no border at rest, white with a primary border when focused, an
 * error border with its message under it. [strength] adds the Sign Up strength bar and [hint] the helper text, which
 * an error replaces. TalkBack reads the label and the error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @DrawableRes icon: Int,
    error: FieldError?,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isPassword: Boolean = false,
    strength: Int? = null,
    hint: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    var visible by rememberSaveable { mutableStateOf(false) }
    val transformation =
        if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None
    val errorText = error?.let { stringResource(it.messageRes()) }
    val isError = errorText != null
    val shape = MaterialTheme.shapes.medium
    val colors = authFieldColors()
    val supportingText: (@Composable () -> Unit)? =
        if (strength == null && errorText == null && hint == null) {
            null
        } else {
            { FieldSupportingText(strength, errorText, hint) }
        }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = Dimens.AuthFieldHeight)
            .semantics { if (errorText != null) error(errorText) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colorScheme.onSurface),
        cursorBrush = SolidColor(if (isError) colorScheme.error else colorScheme.primary),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = true,
        visualTransformation = transformation,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            TextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = true,
                visualTransformation = transformation,
                interactionSource = interactionSource,
                isError = isError,
                label = { Text(stringResource(label)) },
                leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
                trailingIcon = if (isPassword) {
                    { PasswordVisibilityButton(visible, onToggle = { visible = !visible }) }
                } else {
                    null
                },
                supportingText = supportingText,
                shape = shape,
                colors = colors,
                container = { FieldContainer(isError, focused, shape) },
            )
        },
    )
}

@Composable
private fun authFieldColors(): TextFieldColors {
    val colorScheme = MaterialTheme.colorScheme
    return TextFieldDefaults.colors(
        focusedTextColor = colorScheme.onSurface,
        unfocusedTextColor = colorScheme.onSurface,
        errorTextColor = colorScheme.onSurface,
        cursorColor = colorScheme.primary,
        errorCursorColor = colorScheme.error,
        focusedLeadingIconColor = colorScheme.primary,
        unfocusedLeadingIconColor = colorScheme.onSurfaceVariant,
        errorLeadingIconColor = colorScheme.error,
        focusedTrailingIconColor = colorScheme.onSurfaceVariant,
        unfocusedTrailingIconColor = colorScheme.onSurfaceVariant,
        errorTrailingIconColor = colorScheme.onSurfaceVariant,
        focusedLabelColor = colorScheme.primary,
        unfocusedLabelColor = colorScheme.onSurfaceVariant,
        errorLabelColor = colorScheme.error,
        focusedSupportingTextColor = colorScheme.onSurfaceVariant,
        unfocusedSupportingTextColor = colorScheme.onSurfaceVariant,
        errorSupportingTextColor = colorScheme.error,
    )
}

/** The Sign Up strength bar, then the error or else the hint. */
@Composable
private fun FieldSupportingText(strength: Int?, errorText: String?, hint: String?) {
    Column {
        if (strength != null) {
            PasswordStrengthBar(strength)
            Spacer(Modifier.height(Spacing.xsPlus))
        }
        // The field itself carries the error for TalkBack, so this line isn't read a second time.
        if (errorText != null) Text(errorText, Modifier.clearAndSetSemantics {})
        else if (hint != null) Text(hint)
    }
}

/** The eye at the end of a password field. */
@Composable
private fun PasswordVisibilityButton(visible: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            // The icon shows the current state: crossed-out eye while hidden, open eye while visible.
            painter = painterResource(
                if (visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
            ),
            contentDescription = stringResource(
                if (visible) R.string.auth_hide_password else R.string.auth_show_password
            ),
        )
    }
}

/** The field's background: light at rest, white when focused, with the focus or error border. */
@Composable
private fun FieldContainer(isError: Boolean, focused: Boolean, shape: Shape) {
    val colorScheme = MaterialTheme.colorScheme
    val borderColor = when {
        isError -> colorScheme.error
        focused -> colorScheme.primary
        else -> Color.Transparent
    }
    Box(
        Modifier
            .background(
                if (focused) colorScheme.surfaceContainerLowest else colorScheme.surfaceContainerHighest,
                shape,
            )
            .border(Dimens.AuthFieldBorder, borderColor, shape),
    )
}

/** Display only: [AuthValidator.passwordStrength] filled segments out of [AuthValidator.STRENGTH_SEGMENTS]. */
@Composable
private fun PasswordStrengthBar(strength: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
    ) {
        repeat(AuthValidator.STRENGTH_SEGMENTS) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(StrengthBarHeight)
                    .clip(CircleShape)
                    .background(
                        if (index < strength) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    ),
            )
        }
    }
}

/** Full-width 56dp pill with a bold label and an arrow; stays enabled, a tap while [loading] is ignored. */
@Composable
internal fun AuthButton(text: String, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = { if (!loading) onClick() },
        shape = CircleShape,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = ButtonElevation, pressedElevation = ButtonPressedElevation),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.AuthFieldHeight)
            .semantics { contentDescription = text },
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(IconSize.l),
                color = LocalContentColor.current,
                strokeWidth = Dimens.ProgressStroke,
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(Modifier.width(Spacing.s))
            Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, Modifier.size(IconSize.m))
        }
    }
}

/** "New here? Create an account" / "Already have an account? Sign in": only the last words are a button. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SwitchModeLine(isSignUp: Boolean, onClick: () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(if (isSignUp) R.string.auth_have_account else R.string.auth_new_here),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(if (isSignUp) R.string.auth_sign_in_link else R.string.auth_create_account_link),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.xsPlus, vertical = Spacing.xs),
        )
    }
}

@StringRes
private fun FieldError.messageRes(): Int = when (this) {
    FieldError.EMPTY_NAME -> R.string.error_empty_name
    FieldError.EMPTY_EMAIL -> R.string.error_empty_email
    FieldError.INVALID_EMAIL -> R.string.error_invalid_email
    FieldError.EMPTY_PASSWORD -> R.string.error_empty_password
    FieldError.PASSWORD_TOO_SHORT -> R.string.error_password_too_short
    FieldError.PASSWORDS_DONT_MATCH -> R.string.error_passwords_dont_match
    FieldError.EMPTY_CONFIRM -> R.string.error_empty_confirm
    FieldError.WRONG_CREDENTIALS -> R.string.error_wrong_credentials
    FieldError.EMAIL_IN_USE -> R.string.error_email_in_use
    FieldError.WEAK_PASSWORD -> R.string.error_weak_password
}

@Composable
internal fun messageText(message: AuthMessage): String = when (message) {
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

/** The password strength segments. */
private val StrengthBarHeight = 4.dp

/** The primary button's shadow, at rest and pressed. */
private val ButtonElevation = 6.dp
private val ButtonPressedElevation = 2.dp
