package com.example.planyourtrip.ui

import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.auth.AuthContent
import com.example.planyourtrip.ui.auth.AuthMode
import com.example.planyourtrip.ui.auth.AuthUiState
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormActions
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormContent
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormUiState
import com.example.planyourtrip.ui.tripform.TripFormContent
import com.example.planyourtrip.ui.tripform.TripFormUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Typed text is Compose state saved with rememberSaveable: it survives the activity being recreated, including after
 * process death (emulated by [StateRestorationTester]), and every keystroke still reaches the ViewModel. A typed
 * password is never saved.
 */
@RunWith(AndroidJUnit4::class)
class FormTextRestorationTest {

    @get:Rule
    val rule = createComposeRule()

    private val restoration = StateRestorationTester(rule)

    private fun string(@StringRes id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    /** The text field that shows [text] (its label, or what was typed). */
    private fun field(text: String): SemanticsNodeInteraction = rule.onNode(hasSetTextAction() and hasText(text))

    /** Length of the field's text (a hidden password's characters are dots, but just as many). */
    private fun SemanticsNodeInteraction.editableTextLength(): Int =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.EditableText)?.length ?: 0

    @Test
    fun tripForm_destinationSurvivesRestore() {
        val sent = mutableListOf<String>()
        restoration.setContent {
            PlanYourTripTheme(darkTheme = false) {
                TripFormContent(
                    state = TripFormUiState(),
                    onBack = {}, onDestinationChange = { sent += it }, onStartDateChange = {}, onEndDateChange = {},
                    onChooseCurrency = {}, onClearCurrency = {}, onSave = {},
                )
            }
        }
        field(string(R.string.new_trip_destination)).performTextInput("Lisbon")

        restoration.emulateSavedInstanceStateRestore()

        field("Lisbon").assertExists()
        assertEquals("Lisbon", sent.last())
    }

    @Test
    fun expenseForm_nameAndAmountSurviveRestore() {
        val names = mutableListOf<String>()
        val amounts = mutableListOf<String>()
        restoration.setContent {
            PlanYourTripTheme(darkTheme = false) {
                ExpenseFormContent(
                    state = ExpenseFormUiState(isLoading = false, date = LocalDate.of(2026, 11, 13)),
                    actions = ExpenseFormActions(
                        onNameChange = { names += it }, onAmountChange = { amounts += it }, onCurrencyChange = {},
                        onCategoryChange = {}, onDateChange = {}, onPaidByChange = {}, onSplitToggle = {},
                        onNotShared = {}, onSave = {}, onDelete = {},
                    ),
                    onBack = {},
                )
            }
        }
        field(string(R.string.expense_name)).performTextInput("Taxi")
        field(string(R.string.expense_amount)).performTextInput("1250")

        restoration.emulateSavedInstanceStateRestore()

        field("Taxi").assertExists()
        field("1250").assertExists()
        assertEquals("Taxi", names.last())
        assertEquals("1250", amounts.last())
    }

    @Test
    fun auth_nameAndEmailSurviveRestore_butNotThePassword() {
        restoration.setContent {
            PlanYourTripTheme(darkTheme = false) {
                AuthContent(
                    state = AuthUiState(mode = AuthMode.SIGN_UP),
                    onModeToggle = {}, onNameEdited = {}, onEmailEdited = {}, onPasswordEdited = {},
                    onConfirmEdited = {}, onSubmit = {}, onMessageShown = {},
                )
            }
        }
        field(string(R.string.auth_full_name)).performTextInput("Alex Morgan")
        field(string(R.string.auth_email)).performTextInput("alex@example.com")
        val password = field(string(R.string.auth_password))
        password.performTextInput("travel2026")
        assertEquals(10, password.editableTextLength())

        restoration.emulateSavedInstanceStateRestore()

        field("Alex Morgan").assertExists()
        field("alex@example.com").assertExists()
        assertEquals(0, field(string(R.string.auth_password)).editableTextLength())
    }
}
