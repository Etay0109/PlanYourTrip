package com.example.planyourtrip.ui.auth

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.Spacing

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

/**
 * Sign In / Sign Up (reference: design/sign_in.png, design/sign_up.png): a photo hero edge to edge behind the status
 * bar, then a sheet with rounded top corners holding the [AuthForm]: the fields, the button and the link to the other
 * mode.
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

    val focus = rememberAuthFormFocus(state)
    val density = LocalDensity.current
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
                    .onSizeChanged { focus.layout.viewportHeight = it.height }
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
                        .bringIntoViewRequester(focus.sheetRequester)
                        .onGloballyPositioned { focus.layout.sheet = it }
                        .navigationBarsPadding()
                        .padding(horizontal = Spacing.xxl, vertical = Spacing.xxlPlus),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AuthForm(
                        state = state,
                        onModeToggle = onModeToggle,
                        onNameChange = onNameChange,
                        onEmailChange = onEmailChange,
                        onPasswordChange = onPasswordChange,
                        onConfirmChange = onConfirmChange,
                        onSubmit = onSubmit,
                        focus = focus,
                    )
                }
            }
            StatusBarScrim()
        }
    }
}
