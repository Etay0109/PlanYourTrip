package com.example.planyourtrip.ui.tripdetails.expense

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.currency.ExchangeRate
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.DateField
import com.example.planyourtrip.ui.common.components.EditFormScaffold
import com.example.planyourtrip.ui.common.components.FormDelete
import com.example.planyourtrip.ui.common.components.TintedFilterChip
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.common.iconRes
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.theme.isLargeFontScale
import com.example.planyourtrip.ui.tripdetails.CurrencyConversion
import com.example.planyourtrip.ui.tripdetails.components.TravelerAvatar
import com.example.planyourtrip.ui.tripdetails.components.personName
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Composable
fun ExpenseFormScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ExpenseFormViewModel = viewModel(factory = ExpenseFormViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }
    ExpenseFormContent(
        state = state,
        actions = remember(viewModel) {
            ExpenseFormActions(
                onNameChange = viewModel::onNameChange,
                onAmountChange = viewModel::onAmountChange,
                onCurrencyChange = viewModel::onCurrencyChange,
                onCategoryChange = viewModel::onCategoryChange,
                onDateChange = viewModel::onDateChange,
                onPaidByChange = viewModel::onPaidByChange,
                onSplitToggle = viewModel::onSplitToggle,
                onNotShared = viewModel::onNotShared,
                onSave = viewModel::onSave,
                onDelete = viewModel::onDelete,
            )
        },
        onBack = onBack,
    )
}

/** Callbacks of [ExpenseFormContent], grouped so the screen passes one value. */
class ExpenseFormActions(
    val onNameChange: (String) -> Unit,
    val onAmountChange: (String) -> Unit,
    /** ILS or the foreign currency code. */
    val onCurrencyChange: (String) -> Unit,
    val onCategoryChange: (ExpenseCategory) -> Unit,
    val onDateChange: (LocalDate) -> Unit,
    val onPaidByChange: (String) -> Unit,
    val onSplitToggle: (String) -> Unit,
    val onNotShared: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseFormContent(
    state: ExpenseFormUiState,
    actions: ExpenseFormActions,
    onBack: () -> Unit,
) {
    EditFormScaffold(
        title = stringResource(if (state.isEditing) R.string.expense_edit_title else R.string.expense_new_title),
        isLoading = state.isLoading,
        isSaving = state.isSaving,
        saveText = stringResource(R.string.expense_save),
        onBack = onBack,
        onSave = actions.onSave,
        delete = if (state.isEditing) {
            FormDelete(
                iconDescription = stringResource(R.string.expense_delete),
                dialogTitle = stringResource(R.string.expense_delete_title),
                dialogBody = stringResource(R.string.expense_delete_body, state.name.trim()),
                onDelete = actions.onDelete,
            )
        } else {
            null
        },
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = actions.onNameChange,
            label = { Text(stringResource(R.string.expense_name)) },
            placeholder = { Text(stringResource(R.string.expense_name_placeholder)) },
            singleLine = true,
            isError = state.nameError != null,
            supportingText = errorSupportingText(state.nameError?.nameMessageRes()),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )

        AmountField(state, actions.onAmountChange, actions.onCurrencyChange)

        if (state.showSplit) {
            SplitFields(state, actions.onPaidByChange, actions.onSplitToggle, actions.onNotShared)
        }

        DateField(
            label = R.string.expense_date,
            date = state.date,
            errorRes = state.dateError?.dateMessageRes(),
            // A date already after the trip opens the picker on the last selectable day.
            initialPickerDate = state.tripEnd?.let { minOf(state.date, it) } ?: state.date,
            onDatePicked = actions.onDateChange,
            maxDate = state.tripEnd,
        )

        Text(
            text = stringResource(R.string.expense_category),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ExpenseCategory.entries.forEach { category ->
                // Each chip shows its category color: the icon always, the tinted fill when selected.
                TintedFilterChip(
                    selected = category == state.category,
                    onClick = { actions.onCategoryChange(category) },
                    label = stringResource(category.labelRes()),
                    icon = category.iconRes(),
                    color = ExtendedTheme.colors.category(category),
                )
            }
        }
    }
}

/**
 * "Paid by" (one person) and "Split between" (one or more, plus the quick "Not shared" option). Shown only when the
 * trip has other travelers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SplitFields(
    state: ExpenseFormUiState,
    onPaidByChange: (String) -> Unit,
    onSplitToggle: (String) -> Unit,
    onNotShared: () -> Unit,
) {
    Text(
        text = stringResource(R.string.expense_paid_by),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        state.people.forEach { person ->
            PersonChip(person, selected = person.key == state.paidBy, onClick = { onPaidByChange(person.key) })
        }
    }
    Text(
        text = stringResource(R.string.expense_split_between),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = state.isNotShared,
            onClick = onNotShared,
            label = { Text(stringResource(R.string.expense_not_shared)) },
        )
        state.people.forEach { person ->
            PersonChip(
                person,
                selected = person.key in state.splitBetween,
                onClick = { onSplitToggle(person.key) },
            )
        }
    }
    if (state.splitError != null) {
        Text(
            text = stringResource(R.string.error_split_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** A chip with the person's small avatar and name. */
@Composable
private fun PersonChip(person: Person, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(personName(person), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { TravelerAvatar(person, Dimens.TravelerAvatarChipSize) },
    )
}

/**
 * The amount field with a currency selector next to it (ILS and the trip currency), and under it the live
 * conversion, the rate status, or the error.
 */
@Composable
private fun AmountField(
    state: ExpenseFormUiState,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
) {
    BoxWithConstraints {
        // Side by side on regular screens; the selector moves under the field when narrow or with large text.
        val stacked = maxWidth < Dimens.CompactScreenWidth || isLargeFontScale()
        if (stacked) {
            Column {
                AmountTextField(state, onAmountChange, Modifier.fillMaxWidth())
                CurrencySelector(state, onCurrencyChange, Modifier.padding(top = 8.dp))
            }
        } else {
            Row(verticalAlignment = Alignment.Top) {
                AmountTextField(state, onAmountChange, Modifier.weight(1f))
                CurrencySelector(state, onCurrencyChange, Modifier.padding(start = 8.dp, top = 8.dp))
            }
        }
    }
    AmountStatus(state)
}

/** The amount, prefixed with the selected currency's symbol (₪, €, ¥; none when the platform has no symbol). */
@Composable
private fun AmountTextField(state: ExpenseFormUiState, onAmountChange: (String) -> Unit, modifier: Modifier) {
    val code = state.currencyCode
    val symbol = if (code == ILS) stringResource(R.string.currency_symbol) else CurrencyConversion.currencySymbol(code)
    OutlinedTextField(
        value = state.amount,
        onValueChange = onAmountChange,
        label = { Text(stringResource(R.string.expense_amount)) },
        prefix = symbol?.let { { Text(it) } },
        singleLine = true,
        isError = state.amountError != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    )
}

/** ILS and the foreign currency as a two-option segmented button; nothing when only ILS is possible. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySelector(state: ExpenseFormUiState, onCurrencyChange: (String) -> Unit, modifier: Modifier) {
    val foreign = state.foreignCode ?: return
    val options = listOf(ILS, foreign)
    val description = stringResource(R.string.expense_currency)
    SingleChoiceSegmentedButtonRow(modifier = modifier.semantics { contentDescription = description }) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == state.currencyCode,
                onClick = { onCurrencyChange(option) },
                // The trip currency is disabled when no rate is available at all.
                enabled = option == ILS || state.foreignEnabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                label = { Text(option, maxLines = 1) },
            )
        }
    }
}

/** Error, live conversion or rate status under the amount (nothing for a plain shekel amount). */
@Composable
private fun AmountStatus(state: ExpenseFormUiState) {
    val error = state.amountError
    val foreign = state.foreignCode
    val preview = state.preview
    val (text, color) = when {
        error != null -> amountErrorText(error, state) to MaterialTheme.colorScheme.error
        preview != null -> stringResource(
            R.string.expense_conversion,
            shekels(preview.agorot),
            preview.currencyCode,
            CurrencyConversion.formatRate(preview.ilsPerUnit),
            TripFormatting.dayMonth(preview.rateDate),
        ) to MaterialTheme.colorScheme.primary
        foreign == null -> return
        !state.foreignEnabled ->
            stringResource(R.string.expense_rate_unavailable, foreign) to MaterialTheme.colorScheme.onSurfaceVariant
        state.foreignRate == ForeignRateState.Loading && state.currencyCode == foreign ->
            stringResource(R.string.expense_rate_loading, foreign) to MaterialTheme.colorScheme.onSurfaceVariant
        else -> return
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun amountErrorText(error: ExpenseFieldError, state: ExpenseFormUiState): String = when (error) {
    ExpenseFieldError.EMPTY_NAME -> stringResource(R.string.error_empty_expense_name)
    ExpenseFieldError.EMPTY_AMOUNT -> stringResource(R.string.error_empty_amount)
    ExpenseFieldError.DATE_AFTER_TRIP -> stringResource(R.string.error_expense_after_trip)
    ExpenseFieldError.SPLIT_EMPTY -> stringResource(R.string.error_split_empty)
    ExpenseFieldError.NO_RATE ->
        if (state.foreignRate == ForeignRateState.Unavailable) {
            stringResource(R.string.expense_rate_unavailable, state.currencyCode)
        } else {
            stringResource(R.string.error_no_rate)
        }
    ExpenseFieldError.INVALID_AMOUNT -> {
        val code = state.currencyCode
        val digits = if (code == ILS) 2 else ExpenseCurrency.fractionDigits(code)
        if (digits == 0) stringResource(R.string.error_invalid_amount_whole)
        else pluralStringResource(R.plurals.error_invalid_amount_decimals, digits, digits)
    }
}

/** The name field's only error. */
@StringRes
private fun ExpenseFieldError.nameMessageRes(): Int = R.string.error_empty_expense_name

/** The date field's only error. */
@StringRes
private fun ExpenseFieldError.dateMessageRes(): Int = R.string.error_expense_after_trip

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
private fun ExpenseFormForeignPreview() = Preview(previewForeign)

@Preview(name = "Expense form – EUR, dark", showBackground = true, heightDp = 900)
@Composable
private fun ExpenseFormForeignDarkPreview() = Preview(previewForeign, darkTheme = true)

@Preview(name = "Expense form – no rate, invalid amount", showBackground = true, heightDp = 900)
@Composable
private fun ExpenseFormNoRatePreview() = Preview(
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
private fun ExpenseFormShekelPreview() =
    Preview(ExpenseFormUiState(isLoading = false, amount = "85.50", date = LocalDate.of(2026, 11, 13)))

@Preview(name = "Expense form – not shared, empty split error", showBackground = true, heightDp = 900)
@Composable
private fun ExpenseFormSplitErrorPreview() = Preview(
    previewForeign.copy(splitBetween = emptyList(), splitError = ExpenseFieldError.SPLIT_EMPTY)
)

@Preview(name = "Expense form – narrow, large font", widthDp = 320, heightDp = 1100, fontScale = 1.5f)
@Composable
private fun ExpenseFormNarrowPreview() = Preview(previewForeign.copy(amount = "1500", currencyCode = "EUR"))
