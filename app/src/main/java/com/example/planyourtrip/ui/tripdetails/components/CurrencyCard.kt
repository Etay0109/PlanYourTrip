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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.components.CurrencyCodeLabel
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.currencyFlag
import com.example.planyourtrip.ui.theme.minFitTextScale
import com.example.planyourtrip.ui.tripdetails.RateLine
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.theme.isLargeFontScale
import com.example.planyourtrip.ui.tripdetails.ConverterInput
import com.example.planyourtrip.ui.tripdetails.ConverterSide
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
        shadowElevation = Dimens.CardElevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xxl)) {
            when (state) {
                CurrencyCardState.NoCurrency -> NoCurrency(actions.onChooseCurrency)
                CurrencyCardState.Shekel -> CodeWithMessage(ILS, actions.onChooseCurrency) {
                    MutedText(stringResource(R.string.currency_shekel_body), Modifier.weight(1f))
                }
                is CurrencyCardState.Loading -> CodeWithMessage(state.code, actions.onChooseCurrency) {
                    CircularProgressIndicator(modifier = Modifier.size(IconSize.s), strokeWidth = Dimens.ProgressStroke)
                    Spacer(Modifier.width(Spacing.s))
                    MutedText(stringResource(R.string.currency_loading), Modifier.weight(1f))
                }
                is CurrencyCardState.Unavailable -> {
                    CodeWithMessage(state.code, actions.onChooseCurrency) {
                        MutedText(stringResource(R.string.currency_unavailable), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Spacing.xl))
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
        Spacer(Modifier.width(Spacing.m))
        IconChip(R.drawable.ic_currency_exchange)
    }
    Spacer(Modifier.height(Spacing.xl))
    PrimaryButton(text = stringResource(R.string.currency_choose), onClick = onChoose)
}

/** The code chip followed by a short status line. */
@Composable
private fun CodeWithMessage(code: String, onChoose: () -> Unit, message: @Composable RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CodeChip(code, onChoose)
        Spacer(Modifier.width(Spacing.m))
        message()
    }
}

@Composable
private fun WithRate(state: CurrencyCardState.Rate, actions: CurrencyCardActions, initialConverter: ConverterInput) {
    BoxWithConstraints {
        // The date pill moves under the rate on narrow cards and large fonts.
        val inline = maxWidth >= Dimens.NarrowContentWidth && !isLargeFontScale()
        if (inline) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RateHeadline(state, actions.onChooseCurrency, Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.s))
                UpdatedPill(state)
            }
        } else {
            Column {
                RateHeadline(state, actions.onChooseCurrency)
                Spacer(Modifier.height(Spacing.s))
                UpdatedPill(state)
            }
        }
    }
    Spacer(Modifier.height(Spacing.l))
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

/**
 * "1 [EUR ▾] = ₪3.44", or for a currency worth less than ₪0.10 "₪1 = 495.52 [ARS ▾]" ([CurrencyConversion.rateLine]);
 * the code chip opens the currency picker. Always one line: when it doesn't fit (a narrow card, a large font) the text
 * gets a little smaller, down to [minFitTextScale]; the chip keeps its size.
 */
@Composable
private fun RateHeadline(state: CurrencyCardState.Rate, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.titleLarge
    val color = MaterialTheme.colorScheme.onSurface
    val (before, after) = when (val line = CurrencyConversion.rateLine(state.ilsPerUnit)) {
        is RateLine.PerUnit -> stringResource(R.string.currency_rate_one) to
            stringResource(R.string.currency_rate_value, stringResource(R.string.money_shekels, line.shekels))
        is RateLine.PerShekel -> stringResource(R.string.currency_rate_per_shekel, line.units) to ""
    }
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val chipStyle = MaterialTheme.typography.labelLarge
        val minScale = minFitTextScale(density.fontScale)
        val maxWidthPx = constraints.maxWidth
        val textStyle = remember(before, after, state.code, style, chipStyle, maxWidthPx, density, minScale) {
            fun width(text: String, textStyle: TextStyle) =
                if (text.isEmpty()) 0 else measurer.measure(text, textStyle, maxLines = 1, softWrap = false).size.width
            val flag = currencyFlag(state.code)?.let { "$it " }.orEmpty()
            val chip = with(density) {
                maxOf(
                    width(flag + state.code, chipStyle) + ChipFixedWidth.roundToPx(),
                    Dimens.TouchTarget.roundToPx(),
                )
            }
            val gaps = with(density) { (RateGap * if (after.isEmpty()) 1 else 2).roundToPx() }
            val texts = width(before, style) + width(after, style)
            val room = (maxWidthPx - chip - gaps).toFloat()
            val scale = if (texts <= room || texts == 0) 1f else (room / texts).coerceIn(minScale, 1f)
            if (scale >= 1f) style else style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RateText(before, textStyle, color)
            Spacer(Modifier.width(RateGap))
            CodeChip(state.code, onChoose)
            if (after.isNotEmpty()) {
                Spacer(Modifier.width(RateGap))
                RateText(after, textStyle, color)
            }
        }
    }
}

@Composable
private fun RateText(text: String, style: TextStyle, color: Color) {
    Text(text, style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

private val RateGap = Spacing.xsPlus

/** The code chip's width besides its label: 10 + 4 dp padding, the 18 dp arrow and the flag's 4 dp gap. */
private val ChipFixedWidth = 36.dp

/** Small outlined chip with the currency code and a dropdown arrow; opens the currency picker. */
@Composable
private fun CodeChip(code: String, onClick: () -> Unit) {
    val description = stringResource(R.string.currency_change, code)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = ExtendedTheme.colors.iconChip,
        border = BorderStroke(Dimens.Hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.sPlus, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CurrencyCodeLabel(code, MaterialTheme.typography.labelLarge, MaterialTheme.colorScheme.primary)
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.s),
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
            .padding(horizontal = Spacing.m, vertical = Spacing.xsPlus),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
    ) {
        Box(
            Modifier
                .size(Dimens.DotSmall)
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
internal fun LargeAmountPreview() = PlanYourTripTheme {
    CurrencyCard(
        state = CurrencyCardState.Rate("EUR", BigDecimal("3.4408"), LocalDate.of(2026, 10, 2)),
        actions = CurrencyCardActions(onChooseCurrency = {}, onRetry = {}),
        initialConverter = ConverterInput(text = "9999999.99"),
        modifier = Modifier.padding(Dimens.ScreenPadding),
    )
}

@Preview(name = "Converter – JPY from shekels")
@Composable
internal fun JpyPreview() = PlanYourTripTheme {
    CurrencyCard(
        state = CurrencyCardState.Rate("JPY", BigDecimal("0.01944"), LocalDate.of(2026, 10, 2)),
        actions = CurrencyCardActions(onChooseCurrency = {}, onRetry = {}),
        initialConverter = ConverterInput(ConverterSide.ILS, "1234567.89", foreignOnLeft = false),
        modifier = Modifier.padding(Dimens.ScreenPadding),
    )
}

@Preview(name = "Rate per shekel – ARS, narrow, large font", widthDp = 320, fontScale = 1.5f)
@Composable
internal fun ArsPreview() = PlanYourTripTheme {
    CurrencyCard(
        state = CurrencyCardState.Rate("ARS", BigDecimal("0.00201808201485"), LocalDate.of(2026, 10, 12)),
        actions = CurrencyCardActions(onChooseCurrency = {}, onRetry = {}),
        initialConverter = ConverterInput(),
        modifier = Modifier.padding(Dimens.ScreenPadding),
    )
}
