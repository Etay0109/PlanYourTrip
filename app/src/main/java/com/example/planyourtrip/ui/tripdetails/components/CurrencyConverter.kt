package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.components.CurrencyCodeLabel
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.ui.theme.ConverterAmountStyles
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.tripdetails.ConverterState
import com.example.planyourtrip.ui.tripdetails.CurrencyConversion

/** Two amount fields side by side with a swap button between them. */
@Composable
internal fun Converter(
    code: String,
    converter: ConverterState,
    onForeignChange: (String) -> Unit,
    onIlsChange: (String) -> Unit,
    onSwap: () -> Unit,
) {
    val foreignSymbol = remember(code) { CurrencyConversion.currencySymbol(code) }
    val foreign: @Composable (Modifier) -> Unit = { m ->
        AmountField(
            code = code,
            symbol = foreignSymbol,
            text = converter.foreignText,
            onTextChange = onForeignChange,
            amountColor = MaterialTheme.colorScheme.onSurface,
            modifier = m,
        )
    }
    val shekel: @Composable (Modifier) -> Unit = { m ->
        AmountField(
            code = ILS,
            symbol = stringResource(R.string.currency_symbol),
            text = converter.ilsText,
            onTextChange = onIlsChange,
            amountColor = MaterialTheme.colorScheme.primary,
            modifier = m,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExtendedTheme.colors.iconChip, MaterialTheme.shapes.large)
            .padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Swapping recreates both fields, so neither inherits the other's cursor or scroll state.
        key(converter.foreignOnLeft) {
            (if (converter.foreignOnLeft) foreign else shekel)(Modifier.weight(1f))
            SwapButton(onSwap)
            (if (converter.foreignOnLeft) shekel else foreign)(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SwapButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = ExtendedTheme.colors.badgeContainer,
        modifier = Modifier
            .padding(horizontal = Spacing.xs)
            .size(SwapButtonSize),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_swap_horiz),
                contentDescription = stringResource(R.string.currency_swap),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.m),
            )
        }
    }
}

/** White rounded field: code label on top, then the editable amount (decimal keyboard) and the symbol. */
@Composable
private fun AmountField(
    code: String,
    symbol: String?,
    text: String,
    onTextChange: (String) -> Unit,
    amountColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.sPlus, vertical = Spacing.s),
    ) {
        CurrencyCodeLabel(code, MaterialTheme.typography.labelLarge, MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmountInput(code, text, onTextChange, amountColor, Modifier.weight(1f))
            if (symbol != null) {
                Spacer(Modifier.width(Spacing.xs))
                Text(symbol, style = MaterialTheme.typography.titleLarge, color = amountColor, maxLines = 1)
            }
        }
    }
}

/**
 * The amount text field. Its font shrinks step by step ([ConverterAmountStyles]) until the whole number fits
 * the box, so nothing is scrolled out of sight or cut off.
 */
@Composable
private fun AmountInput(
    code: String,
    text: String,
    onTextChange: (String) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.currency_amount_in, code)
    val placeholder = stringResource(R.string.currency_amount_placeholder)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        // Leave room for the cursor after the last digit.
        val available = constraints.maxWidth - with(LocalDensity.current) { CursorRoom.roundToPx() }
        val shown = text.ifEmpty { placeholder }
        val fitted = remember(shown, available, measurer) {
            CurrencyConversion.largestFittingSize(ConverterAmountStyles) { style ->
                measurer.measure(shown, style, maxLines = 1, softWrap = false).size.width <= available
            }
        }
        val style = fitted.copy(color = color, textAlign = TextAlign.End)
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    if (text.isEmpty()) {
                        Text(placeholder, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                    inner()
                }
            },
        )
    }
}

private val CursorRoom = 4.dp

/** The round swap button between the two amounts. */
private val SwapButtonSize = 36.dp
