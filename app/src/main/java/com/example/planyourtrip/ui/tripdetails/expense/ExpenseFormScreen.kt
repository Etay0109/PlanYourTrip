package com.example.planyourtrip.ui.tripdetails.expense

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.currency.ExchangeRate
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.BackTopAppBar
import com.example.planyourtrip.ui.common.components.DateField
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.tripdetails.CurrencyConversion
import com.example.planyourtrip.ui.tripdetails.iconRes
import com.example.planyourtrip.ui.tripdetails.labelRes
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
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onAmountChange = viewModel::onAmountChange,
        onCurrencyChange = viewModel::onCurrencyChange,
        onCategoryChange = viewModel::onCategoryChange,
        onDateChange = viewModel::onDateChange,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseFormContent(
    state: ExpenseFormUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onCategoryChange: (ExpenseCategory) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackTopAppBar(
                onBack = onBack,
                title = stringResource(if (state.isEditing) R.string.expense_edit_title else R.string.expense_new_title),
                actions = {
                    if (state.isEditing && !state.isLoading) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !state.isSaving) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.expense_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // Editing: wait for the expense's values so the fields never flash empty.
        if (state.isLoading) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
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

            AmountField(state, onAmountChange, onCurrencyChange)

            DateField(
                label = R.string.expense_date,
                date = state.date,
                errorRes = state.dateError?.dateMessageRes(),
                // A date already after the trip opens the picker on the last selectable day.
                initialPickerDate = state.tripEnd?.let { minOf(state.date, it) } ?: state.date,
                onDatePicked = onDateChange,
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
                    val categoryColor = ExtendedTheme.colors.category(category)
                    FilterChip(
                        selected = category == state.category,
                        onClick = { onCategoryChange(category) },
                        label = { Text(stringResource(category.labelRes())) },
                        leadingIcon = {
                            Icon(
                                painterResource(category.iconRes()),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            iconColor = categoryColor.content,
                            selectedContainerColor = categoryColor.container,
                            selectedLabelColor = categoryColor.content,
                            selectedLeadingIconColor = categoryColor.content,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = category == state.category,
                            selectedBorderColor = categoryColor.content,
                            selectedBorderWidth = 1.dp,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            PrimaryButton(
                text = stringResource(R.string.expense_save),
                onClick = onSave,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.expense_delete_title)) },
            text = { Text(stringResource(R.string.expense_delete_body, state.name.trim())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
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
        val stacked = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
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
            onBack = {}, onNameChange = {}, onAmountChange = {}, onCurrencyChange = {}, onCategoryChange = {},
            onDateChange = {}, onSave = {}, onDelete = {},
        )
    }
}

private val previewForeign = ExpenseFormUiState(
    isLoading = false,
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

@Preview(name = "Expense form – narrow, large font", widthDp = 320, heightDp = 1100, fontScale = 1.5f)
@Composable
private fun ExpenseFormNarrowPreview() = Preview(previewForeign.copy(amount = "1500", currencyCode = "EUR"))
