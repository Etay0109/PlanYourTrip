package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.theme.ConverterAmountStyles
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.tripdetails.ConverterInput
import com.example.planyourtrip.ui.tripdetails.ConverterSide
import com.example.planyourtrip.ui.tripdetails.ConverterState
import com.example.planyourtrip.ui.tripdetails.CurrencyCardState
import com.example.planyourtrip.ui.tripdetails.CurrencyConversion
import java.math.BigDecimal
import java.time.LocalDate

/** Callbacks of [CurrencyCard], grouped so the screen passes one value. */
class CurrencyCardActions(
    val onChooseCurrency: () -> Unit,
    val onRetry: () -> Unit,
)

/**
 * Overview currency card (`design/trip_overview.png`): "1 EUR = ₪3.44" with the rate's date and a two-field
 * converter. The currency code is a small chip with a dropdown arrow that opens the picker. Without a currency it shows a message and
 * "Choose currency"; for a shekel trip a one-line message; without a cached rate a spinner or "Try again".
 */
@Composable
fun CurrencyCard(
    state: CurrencyCardState,
    actions: CurrencyCardActions,
    modifier: Modifier = Modifier,
    /** Starting converter amounts (previews only). */
    initialConverter: ConverterInput = ConverterInput(),
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            when (state) {
                CurrencyCardState.NoCurrency -> NoCurrency(actions.onChooseCurrency)
                CurrencyCardState.Shekel -> CodeWithMessage(ILS, actions.onChooseCurrency) {
                    MutedText(stringResource(R.string.currency_shekel_body), Modifier.weight(1f))
                }
                is CurrencyCardState.Loading -> CodeWithMessage(state.code, actions.onChooseCurrency) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    MutedText(stringResource(R.string.currency_loading), Modifier.weight(1f))
                }
                is CurrencyCardState.Unavailable -> {
                    CodeWithMessage(state.code, actions.onChooseCurrency) {
                        MutedText(stringResource(R.string.currency_unavailable), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(20.dp))
                    PrimaryButton(text = stringResource(R.string.currency_try_again), onClick = actions.onRetry)
                }
                is CurrencyCardState.Rate -> WithRate(state, actions, initialConverter)
            }
        }
    }
}

@Composable
private fun NoCurrency(onChoose: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        MutedText(stringResource(R.string.currency_empty_body), Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        IconChip(R.drawable.ic_currency_exchange)
    }
    Spacer(Modifier.height(20.dp))
    PrimaryButton(text = stringResource(R.string.currency_choose), onClick = onChoose)
}

/** The code chip followed by a short status line. */
@Composable
private fun CodeWithMessage(code: String, onChoose: () -> Unit, message: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CodeChip(code, onChoose)
        Spacer(Modifier.width(12.dp))
        message()
    }
}

@Composable
private fun WithRate(state: CurrencyCardState.Rate, actions: CurrencyCardActions, initialConverter: ConverterInput) {
    BoxWithConstraints {
        // The date pill moves under the rate on narrow cards and large fonts.
        val inline = maxWidth >= 300.dp && LocalDensity.current.fontScale <= 1.2f
        if (inline) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RateHeadline(state, actions.onChooseCurrency, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                UpdatedPill(state)
            }
        } else {
            Column {
                RateHeadline(state, actions.onChooseCurrency)
                Spacer(Modifier.height(8.dp))
                UpdatedPill(state)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    // The converter text is Compose state, updated synchronously while typing (never echoed through the
    // ViewModel). Keyed by the currency code, so choosing another currency clears both fields.
    var input by rememberSaveable(state.code, stateSaver = ConverterInputSaver) { mutableStateOf(initialConverter) }
    Converter(
        code = state.code,
        converter = CurrencyConversion.converterState(input, state.ilsPerUnit),
        onForeignChange = { input = CurrencyConversion.edit(input, ConverterSide.FOREIGN, it) },
        onIlsChange = { input = CurrencyConversion.edit(input, ConverterSide.ILS, it) },
        onSwap = { input = CurrencyConversion.swap(input) },
    )
}

private val ConverterInputSaver = listSaver<ConverterInput, String>(
    save = { listOf(it.source.name, it.text, it.foreignOnLeft.toString()) },
    restore = { ConverterInput(ConverterSide.valueOf(it[0]), it[1], it[2].toBoolean()) },
)

/** "1 [EUR ▾] = ₪3.44" — the code chip opens the currency picker. */
@Composable
private fun RateHeadline(state: CurrencyCardState.Rate, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.titleLarge
    val color = MaterialTheme.colorScheme.onSurface
    val rate = stringResource(R.string.money_shekels, CurrencyConversion.formatRate(state.ilsPerUnit))
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.currency_rate_one), style = style, color = color)
        Spacer(Modifier.width(6.dp))
        CodeChip(state.code, onChoose)
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.currency_rate_value, rate), style = style, color = color)
    }
}

/** Small outlined chip with the currency code and a dropdown arrow; opens the currency picker. */
@Composable
private fun CodeChip(code: String, onClick: () -> Unit) {
    val description = stringResource(R.string.currency_change, code)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = ExtendedTheme.colors.iconChip,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(code, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** "• Updated 2 Oct" with the date the service gave for the rate. */
@Composable
private fun UpdatedPill(state: CurrencyCardState.Rate) {
    val colors = ExtendedTheme.colors
    Row(
        modifier = Modifier
            .background(colors.badgeContainer, CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Text(
            text = stringResource(R.string.currency_updated, TripFormatting.dayMonth(state.date)),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onBadge,
            maxLines = 1,
        )
    }
}

/** Two amount fields side by side with a swap button between them. */
@Composable
private fun Converter(
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
            .padding(4.dp),
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
            .padding(horizontal = 4.dp)
            .size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_swap_horiz),
                contentDescription = stringResource(R.string.currency_swap),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(code, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AmountInput(code, text, onTextChange, amountColor, Modifier.weight(1f))
            if (symbol != null) {
                Spacer(Modifier.width(4.dp))
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

@Composable
private fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

// ---------- Previews ----------

@Preview(name = "Converter – 7 digits, narrow, large font", widthDp = 320, fontScale = 1.5f)
@Composable
private fun LargeAmountPreview() = PlanYourTripTheme {
    CurrencyCard(
        state = CurrencyCardState.Rate("EUR", BigDecimal("3.4408"), LocalDate.of(2026, 10, 2)),
        actions = CurrencyCardActions(onChooseCurrency = {}, onRetry = {}),
        initialConverter = ConverterInput(text = "9999999.99"),
        modifier = Modifier.padding(Dimens.ScreenPadding),
    )
}

@Preview(name = "Converter – JPY from shekels")
@Composable
private fun JpyPreview() = PlanYourTripTheme {
    CurrencyCard(
        state = CurrencyCardState.Rate("JPY", BigDecimal("0.01944"), LocalDate.of(2026, 10, 2)),
        actions = CurrencyCardActions(onChooseCurrency = {}, onRetry = {}),
        initialConverter = ConverterInput(ConverterSide.ILS, "1234567.89", foreignOnLeft = false),
        modifier = Modifier.padding(Dimens.ScreenPadding),
    )
}
