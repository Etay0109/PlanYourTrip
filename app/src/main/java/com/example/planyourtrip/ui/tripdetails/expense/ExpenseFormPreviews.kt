package com.example.planyourtrip.ui.tripdetails.expense

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.example.planyourtrip.data.currency.ExchangeRate
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val previewRate = ExchangeRate("EUR", BigDecimal("4.02"), LocalDate.of(2026, 10, 2), Instant.EPOCH)

@Composable
private fun Preview(state: ExpenseFormUiState, darkTheme: Boolean = false) {
    PlanYourTripTheme(darkTheme = darkTheme) {
        ExpenseFormContent(
            state = state,
            actions = ExpenseFormActions(
                onNameChange = {}, onAmountChange = {}, onCurrencyChange = {}, onCategoryChange = {}, onDateChange = {},
                onPaidByChange = {}, onSplitToggle = {}, onNotShared = {}, onSave = {}, onDelete = {},
            ),
            onBack = {},
        )
    }
}

private const val PREVIEW_ME = "me-uid"

private val previewPeople = listOf(
    Person.You(PREVIEW_ME),
    Person.Named("noa", "Noa", TravelerColor.ROSE),
    Person.Named("amit", "Amit", TravelerColor.INDIGO),
)

private val previewForeign = ExpenseFormUiState(
    isLoading = false,
    people = previewPeople,
    paidBy = "noa",
    splitBetween = listOf(PREVIEW_ME, "noa", "amit"),
    name = "Lunch at Campo de' Fiori",
    amount = "10",
    currencyCode = "EUR",
    foreignCode = "EUR",
    foreignRate = ForeignRateState.Available(previewRate),
    preview = ConversionPreview(4_020, "EUR", previewRate.ilsPerUnit, previewRate.date),
    category = ExpenseCategory.FOOD,
    date = LocalDate.of(2026, 11, 13),
)

@Preview(name = "Expense form – in EUR", showBackground = true, heightDp = 900)
@Composable
internal fun ExpenseFormForeignPreview() = Preview(previewForeign)

@Preview(name = "Expense form – EUR, dark", showBackground = true, heightDp = 900)
@Composable
internal fun ExpenseFormForeignDarkPreview() = Preview(previewForeign, darkTheme = true)

@Preview(name = "Expense form – no rate, invalid amount", showBackground = true, heightDp = 900)
@Composable
internal fun ExpenseFormNoRatePreview() = Preview(
    ExpenseFormUiState(
        isLoading = false,
        isEditing = true,
        name = "Colosseum guided tour",
        amount = "0",
        foreignCode = "EUR",
        foreignRate = ForeignRateState.Unavailable,
        foreignEnabled = false,
        category = ExpenseCategory.ATTRACTIONS,
        date = LocalDate.of(2026, 11, 13),
        amountError = ExpenseFieldError.INVALID_AMOUNT,
    )
)

@Preview(name = "Expense form – trip without currency", showBackground = true, heightDp = 900)
@Composable
internal fun ExpenseFormShekelPreview() =
    Preview(ExpenseFormUiState(isLoading = false, amount = "85.50", date = LocalDate.of(2026, 11, 13)))

@Preview(name = "Expense form – not shared, empty split error", showBackground = true, heightDp = 900)
@Composable
internal fun ExpenseFormSplitErrorPreview() = Preview(
    previewForeign.copy(splitBetween = emptyList(), splitError = ExpenseFieldError.SPLIT_EMPTY)
)

@Preview(name = "Expense form – narrow, large font", widthDp = 320, heightDp = 1100, fontScale = 1.5f)
@Composable
internal fun ExpenseFormNarrowPreview() = Preview(previewForeign.copy(amount = "1500", currencyCode = "EUR"))
