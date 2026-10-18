package com.example.planyourtrip.ui.auth

import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Sign In / Sign Up with the real [AuthViewModel] and a repository that never answers (nothing reaches Firebase, the
 * device's account is never touched): no mode toggle, the bottom links, the empty-field messages and focus, the 320dp
 * / 1.5 layout. The screenshot cases save full-screen pictures (status bar and keyboard included) to the app's
 * files/auth-shots/.
 */
@RunWith(AndroidJUnit4::class)
class AuthScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val repository = object : AuthRepository {
        override val currentUser: StateFlow<AuthUser?> = MutableStateFlow(null)
        val calls = mutableListOf<String>()
        override suspend fun signIn(email: String, password: String): Result<Unit> {
            calls += "signIn"
            return Result.success(Unit)
        }
        override suspend fun signUp(fullName: String, email: String, password: String): Result<Unit> {
            calls += "signUp"
            return Result.success(Unit)
        }
        override fun signOut() = Unit
    }

    /** Like MainActivity: edge to edge with adjustResize, so Compose sees the keyboard's height. */
    private fun show(fontScale: Float? = null, width: Dp? = null) {
        val viewModel = AuthViewModel(repository)
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            @Suppress("DEPRECATION")
            rule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        rule.setContent {
            val content = @Composable {
                PlanYourTripTheme(darkTheme = false) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    AuthContent(
                        state = state,
                        onModeToggle = viewModel::onModeToggle,
                        onNameEdited = viewModel::onNameEdited,
                        onEmailEdited = viewModel::onEmailEdited,
                        onPasswordEdited = viewModel::onPasswordEdited,
                        onConfirmEdited = viewModel::onConfirmEdited,
                        onSubmit = viewModel::onSubmit,
                        onMessageShown = viewModel::onMessageShown,
                    )
                }
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale ?: density.fontScale)) {
                Box(if (width == null) Modifier.fillMaxSize() else Modifier.width(width).fillMaxHeight()) { content() }
            }
        }
    }

    private fun field(label: String) = rule.onNode(hasSetTextAction() and hasText(label))

    private fun hasError(message: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.Error, message)

    @Test
    fun signIn_hasNoModeToggle_andLinksToSignUp() {
        show()
        rule.onNodeWithText("Every trip,\nin one place.").assertIsDisplayed()
        rule.onNodeWithText("Sign Up").assertDoesNotExist()
        rule.onNodeWithText("Full name").assertDoesNotExist()
        rule.onNodeWithText("New here?").assertIsDisplayed()

        field("Email").performTextInput("dana@example.com")
        rule.onNodeWithText("Create an account").performScrollTo().performClick()

        // The keyboard is open after typing, so the hero may be scrolled away: it only has to exist.
        rule.onNodeWithText("Plan your next\nadventure.").assertExists()
        field("Full name").assertExists()
        field("Email").assert(hasText("dana@example.com")) // the email carries over
        rule.onNodeWithText("Create account").assertExists()
        rule.onNodeWithText("At least 8 characters", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Already have an account?").assertExists()

        rule.onNodeWithText("Sign in").performScrollTo().performClick()
        rule.onNodeWithText("Every trip,\nin one place.").assertExists()
        field("Full name").assertDoesNotExist()
        field("Email").assert(hasText("dana@example.com"))
    }

    @Test
    fun signIn_emptyFields_showMessages_andFocusEmail() {
        show()
        rule.onNodeWithText("Sign In").performClick()

        field("Email").assert(hasError("Please enter your email")).assertIsFocused()
        field("Password").assert(hasError("Please enter your password"))
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun signUp_emptyFields_showMessages_andFocusName() {
        show()
        rule.onNodeWithText("Create an account").performClick()
        rule.onNodeWithText("Create account").performClick()

        listOf(
            "Full name" to "Please enter your full name",
            "Email" to "Please enter your email",
            "Password" to "Please enter your password",
            "Confirm password" to "Please confirm your password",
        ).forEach { (label, message) ->
            field(label).assert(hasError(message))
        }
        // The error replaces the helper text under the password.
        rule.onNodeWithText("At least 8 characters", useUnmergedTree = true).assertDoesNotExist()
        field("Full name").assertIsFocused()
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun signIn_errorMovesFocusToThePassword_whenTheEmailIsFine() {
        show()
        field("Email").performTextInput("dana@example.com")
        rule.onNodeWithText("Sign In").performClick()
        field("Password").assert(hasError("Please enter your password")).assertIsFocused()
    }

    @Test
    fun smallPhoneLargeFont_heroTextFits_andTheSheetScrolls() {
        show(fontScale = 1.5f, width = 320.dp)
        rule.waitForIdle()
        saveRoot("5_sign_in_320dp_font_1.5")
        rule.onNodeWithText("Create an account").performScrollTo().performClick()
        rule.waitForIdle()
        // The hero grows with its text, so the subtitle ends above the sheet that overlaps the hero by 32dp.
        val subtitle = rule.onNodeWithText("Create a free account to start planning.").fetchSemanticsNode().boundsInRoot
        val name = field("Full name").fetchSemanticsNode().boundsInRoot
        assertTrue("subtitle $subtitle overlaps the first field $name", subtitle.bottom < name.top)
        saveRoot("6_sign_up_320dp_font_1.5")
        rule.onNodeWithText("Already have an account?").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Sign in").performScrollTo().assertIsDisplayed()
    }

    // ---- Screenshots (full screen, so the status bar and the keyboard are in the picture) ----

    @Test
    fun screenshot_signIn_empty() {
        show()
        rule.waitForIdle()
        savePhone("1_sign_in_empty")
    }

    @Test
    fun screenshot_signUp_empty() {
        show()
        rule.onNodeWithText("Create an account").performClick()
        rule.waitForIdle()
        Thread.sleep(1_000) // the link's ripple fades out
        savePhone("2_sign_up_empty")
    }

    @Test
    fun screenshot_signUp_keyboardOpen() {
        show()
        rule.onNodeWithText("Create an account").performClick()
        field("Full name").performTextInput("Alex Morgan")
        field("Email").performTextInput("alex@example.com")
        field("Password").performTextInput("travel2026")
        field("Password").performClick()
        waitForKeyboard()
        // The focused field and the button are both above the keyboard.
        val view = rule.activity.window.decorView
        val keyboardTop = view.height -
            ViewCompat.getRootWindowInsets(view)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
        listOf(field("Password"), rule.onNodeWithText("Create account")).forEach { node ->
            val semantics = node.fetchSemanticsNode()
            val visible = semantics.boundsInWindow // clipped to what the screen shows
            assertTrue("$visible goes under the keyboard at $keyboardTop", visible.bottom <= keyboardTop)
            assertEquals("partly hidden: $visible", semantics.size.height.toFloat(), visible.height, 1f)
        }
        savePhone("3_sign_up_keyboard_open")
    }

    @Test
    fun screenshot_signIn_emptySubmit() {
        show()
        rule.onNodeWithText("Sign In").performClick()
        // Focus moves to the email field, which opens the keyboard (when the device shows one).
        runCatching { waitForKeyboard() }
        rule.waitForIdle()
        savePhone("4_sign_in_empty_submit")
    }

    private fun saveRoot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(rule.activity.filesDir, "auth-shots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitForKeyboard() {
        val view = rule.activity.window.decorView
        rule.waitUntil(5_000) {
            ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        Thread.sleep(800) // the keyboard's animation
        // The test clock only moves when the test moves it: let the screen's short settle delay and its scroll run.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
    }

    private fun savePhone(name: String) {
        Thread.sleep(300)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(rule.activity.filesDir, "auth-shots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
