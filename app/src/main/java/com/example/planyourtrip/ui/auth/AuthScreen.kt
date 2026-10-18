package com.example.planyourtrip.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.auth.AuthError
import com.example.planyourtrip.ui.theme.AuthHeadlineStyle
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

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
        onMessageShown = viewModel::onMessageShown,
    )
}

/** The form's fields, top to bottom (Sign In shows only [EMAIL] and [PASSWORD]). */
private enum class AuthField { NAME, EMAIL, PASSWORD, CONFIRM }

/** Where the sheet, the fields and the button are, so the focused field and the button can be scrolled above the keyboard. */
private class AuthLayout {
    var viewportHeight = 0
    var sheet: LayoutCoordinates? = null
    var button: LayoutCoordinates? = null
    val fields = mutableMapOf<AuthField, LayoutCoordinates>()
}

/**
 * Sign In / Sign Up (reference: design/sign_in.png, design/sign_up.png): a photo hero edge to edge behind the status
 * bar, then a sheet with rounded top corners holding the fields, the button and the link to the other mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuthContent(
    state: AuthUiState,
    onModeToggle: () -> Unit,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onSubmit: () -> Unit,
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
    WhiteStatusBarIcons()

    val focusRequesters = remember { AuthField.entries.associateWith { FocusRequester() } }
    // A submit stopped by validation moves focus to the first field that needs attention.
    LaunchedEffect(state.failedSubmits) {
        if (state.failedSubmits == 0) return@LaunchedEffect
        firstFieldWithError(state)?.let { focusRequesters.getValue(it).requestFocus() }
    }

    val layout = remember { AuthLayout() }
    val sheetRequester = remember { BringIntoViewRequester() }
    var focusedField by remember { mutableStateOf<AuthField?>(null) }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    // Whenever a field gets focus or the keyboard moves, scroll so the field and (if it fits) the button are visible.
    LaunchedEffect(focusedField) {
        val field = focusedField ?: return@LaunchedEffect
        snapshotFlow { ime.getBottom(density) }.collectLatest {
            delay(KEYBOARD_SETTLE_MILLIS)
            val sheet = layout.sheet?.takeIf { it.isAttached } ?: return@collectLatest
            val fieldBox = layout.fields[field]?.takeIf { it.isAttached }?.let { sheet.localBoundingBoxOf(it) }
                ?: return@collectLatest
            val buttonBottom = layout.button?.takeIf { it.isAttached }?.let { sheet.localBoundingBoxOf(it).bottom }
                ?: fieldBox.bottom
            val bottom = minOf(buttonBottom, fieldBox.top + layout.viewportHeight)
            sheetRequester.bringIntoView(Rect(0f, fieldBox.top, sheet.size.width.toFloat(), bottom))
        }
    }

    val focusManager = LocalFocusManager.current
    fun Modifier.field(field: AuthField) = this
        .focusRequester(focusRequesters.getValue(field))
        .onFocusChanged {
            if (it.isFocused) focusedField = field else if (focusedField == field) focusedField = null
        }
        .onGloballyPositioned { layout.fields[field] = it }

    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLowest
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = sheetColor,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding().imePadding()) },
    ) { innerPadding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(innerPadding)) {
            val heroMinHeight = maxHeight * Dimens.AuthHeroHeightFraction
            var heroHeight by remember { mutableStateOf(heroMinHeight) }
            // The photo stays put behind everything (also behind the status bar); only the content scrolls.
            AuthHeroPhoto(isSignUp = isSignUp, height = heroHeight)
            val statusBarHeight = WindowInsets.statusBars.getTop(density)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Scrolling content never draws under the status bar: the clock and icons stay readable.
                    .drawWithContent {
                        clipRect(top = statusBarHeight.toFloat()) { this@drawWithContent.drawContent() }
                    }
                    .imePadding()
                    .onSizeChanged { layout.viewportHeight = it.height }
                    .verticalScroll(rememberScrollState()),
            ) {
                AuthHeroContent(
                    isSignUp = isSignUp,
                    minHeight = heroMinHeight,
                    modifier = Modifier.onSizeChanged { heroHeight = with(density) { it.height.toDp() } },
                )

                // The sheet overlaps the hero's bottom; the screen behind it has the sheet's color, so the sheet
                // always reaches the bottom edge.
                Column(
                    modifier = Modifier
                        .offset(y = -Dimens.AuthSheetCorner)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = Dimens.AuthSheetCorner, topEnd = Dimens.AuthSheetCorner))
                        .background(sheetColor)
                        .bringIntoViewRequester(sheetRequester)
                        .onGloballyPositioned { layout.sheet = it }
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.xxl, vertical = Spacing.xxlPlus),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = FormMaxWidth).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.mPlus),
                    ) {
                        if (isSignUp) {
                            AuthTextField(
                                value = state.fullName,
                                onValueChange = onNameChange,
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
                            value = state.email,
                            onValueChange = onEmailChange,
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
                            value = state.password,
                            onValueChange = onPasswordChange,
                            label = R.string.auth_password,
                            icon = R.drawable.ic_lock_outline,
                            error = state.passwordError,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = if (isSignUp) ImeAction.Next else ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                            isPassword = true,
                            strength = if (isSignUp) AuthValidator.passwordStrength(state.password) else null,
                            hint = if (isSignUp) stringResource(R.string.auth_password_hint) else null,
                            modifier = Modifier.field(AuthField.PASSWORD),
                        )
                        if (isSignUp) {
                            AuthTextField(
                                value = state.confirmPassword,
                                onValueChange = onConfirmChange,
                                label = R.string.auth_confirm_password,
                                icon = R.drawable.ic_lock_outline,
                                error = state.confirmError,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                                isPassword = true,
                                modifier = Modifier.field(AuthField.CONFIRM),
                            )
                        }

                        Spacer(Modifier.height(Spacing.xsPlus))
                        AuthButton(
                            text = stringResource(
                                if (isSignUp) R.string.auth_button_sign_up else R.string.auth_button_sign_in
                            ),
                            loading = state.isLoading,
                            onClick = onSubmit,
                            modifier = Modifier.onGloballyPositioned { layout.button = it },
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        SwitchModeLine(
                            isSignUp = isSignUp,
                            onClick = {
                                focusManager.clearFocus()
                                onModeToggle()
                            },
                        )
                    }
                }
            }
            StatusBarScrim()
        }
    }
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

/**
 * Fixed behind the status bar (it doesn't scroll): dark teal at 40% under the clock and icons, fading out just below
 * them, so they stay readable over a bright sky and over the hero's content when it scrolls up under them.
 */
@Composable
private fun StatusBarScrim() {
    val color = ExtendedTheme.colors.authStatusBarScrim
    val statusBar = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val height = statusBar + Dimens.AuthStatusBarFade
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to color,
                    statusBar / height to color.copy(alpha = color.alpha * 0.75f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/** The hero's photo, [height] tall: a fixed layer under the scrolling [AuthHeroContent]. */
@Composable
private fun AuthHeroPhoto(isSignUp: Boolean, height: Dp) {
    Box(Modifier.fillMaxWidth().height(height)) {
        Image(
            painter = painterResource(if (isSignUp) R.drawable.auth_hero_sign_up else R.drawable.auth_hero_sign_in),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * Gradient, app icon, headline and subtitle over the photo. The gradient scrolls with the text, so the text keeps its
 * dark backing when it moves up over a bright part of the photo. Grows past [minHeight] when its text needs more room.
 */
@Composable
private fun AuthHeroContent(isSignUp: Boolean, minHeight: Dp, modifier: Modifier = Modifier) {
    val colors = ExtendedTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(Brush.verticalGradient(listOf(Color.Transparent, colors.authHeroScrim))),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.xxl, bottom = Dimens.AuthSheetCorner + Spacing.xxlPlus),
        ) {
            AuthAppIcon()
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = stringResource(if (isSignUp) R.string.auth_headline_sign_up else R.string.auth_headline_sign_in),
                style = AuthHeadlineStyle,
                color = colors.onAuthHero,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                text = stringResource(if (isSignUp) R.string.auth_subtitle_sign_up else R.string.auth_subtitle_sign_in),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onAuthHeroMuted,
            )
        }
    }
}

/**
 * The real launcher icon: its background color and foreground (pin + airplane). The foreground is drawn at the
 * adaptive icon's 108/72 scale, so the visible 72dp part fills the square like on the home screen.
 */
@Composable
private fun AuthAppIcon() {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = Modifier
            .size(Dimens.AuthAppIconSize)
            .shadow(elevation = AppIconElevation, shape = shape)
            .clip(shape)
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(Dimens.AuthAppIconSize * (108f / 72f)),
        )
    }
}

/**
 * A 56dp field with 16dp corners: a light fill and no border at rest, white with a primary border when focused, an
 * error border with its message under it. [strength] adds the Sign Up strength bar and [hint] the helper text, which
 * an error replaces. TalkBack reads the label and the error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthTextField(
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
    val colors = TextFieldDefaults.colors(
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
    val supportingText: (@Composable () -> Unit)? =
        if (strength == null && errorText == null && hint == null) {
            null
        } else {
            {
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
                    {
                        IconButton(onClick = { visible = !visible }) {
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
                } else {
                    null
                },
                supportingText = supportingText,
                shape = shape,
                colors = colors,
                container = {
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
                },
            )
        },
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
private fun AuthButton(text: String, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
private fun SwitchModeLine(isSignUp: Boolean, onClick: () -> Unit) {
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

/** White status bar icons over the hero photo while the screen shows; the previous setting comes back after. */
@Composable
private fun WhiteStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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
private fun messageText(message: AuthMessage): String = when (message) {
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

/** On wide screens (tablets, landscape) the form stops growing at this width. */
private val FormMaxWidth = 480.dp

/** The app icon's shadow on the hero. */
private val AppIconElevation = 10.dp

/** The password strength segments. */
private val StrengthBarHeight = 4.dp

/** The primary button's shadow, at rest and pressed. */
private val ButtonElevation = 6.dp
private val ButtonPressedElevation = 2.dp
