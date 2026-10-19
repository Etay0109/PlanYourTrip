package com.example.planyourtrip.ui.tripdetails.expense

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.components.CurrencyCodeLabel
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.theme.isLargeFontScale
import com.example.planyourtrip.ui.tripdetails.CurrencyConversion

/**
 * The amount field with a currency selector next to it (ILS and the trip currency), and under it the live
 * conversion, the rate status, or the error.
 */
@Composable
internal fun AmountField(
    state: ExpenseFormUiState,
    amount: String,
    onAmountChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
) {
    BoxWithConstraints {
        // Side by side on regular screens; the selector moves under the field when narrow or with large text.
        val stacked = maxWidth < Dimens.CompactScreenWidth || isLargeFontScale()
        if (stacked) {
            Column {
                AmountTextField(state, amount, onAmountChange, Modifier.fillMaxWidth())
                CurrencySelector(state, onCurrencyChange, Modifier.padding(top = Spacing.s))
            }
        } else {
            Row(verticalAlignment = Alignment.Top) {
                AmountTextField(state, amount, onAmountChange, Modifier.weight(1f))
                CurrencySelector(state, onCurrencyChange, Modifier.padding(start = Spacing.s, top = Spacing.s))
            }
        }
    }
    AmountStatus(state)
}

/** The amount, prefixed with the selected currency's symbol (₪, €, ¥; none when the platform has no symbol). */
@Composable
private fun AmountTextField(
    state: ExpenseFormUiState,
    amount: String,
    onAmountChange: (String) -> Unit,
    modifier: Modifier,
) {
    val code = state.currencyCode
    val symbol = if (code == ILS) stringResource(R.string.currency_symbol) else CurrencyConversion.currencySymbol(code)
    OutlinedTextField(
        value = amount,
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
                label = { CurrencyCodeLabel(option, LocalTextStyle.current, LocalContentColor.current) },
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
        modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.xs),
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
