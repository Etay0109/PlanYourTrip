package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.ui.common.AppLocale
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Texts of the two converter fields and which one is on the left. */
data class ConverterState(
    val foreignText: String = "",
    val ilsText: String = "",
    val foreignOnLeft: Boolean = true,
)

/** The two converter fields. */
enum class ConverterSide { FOREIGN, ILS }

/**
 * What the user typed in the converter: the last edited [source] field and its [text]. The other field is
 * always computed from it, so a refreshed rate updates it too. [foreignOnLeft] is the swap button's state.
 */
data class ConverterInput(
    val source: ConverterSide = ConverterSide.FOREIGN,
    val text: String = "",
    val foreignOnLeft: Boolean = true,
)

/**
 * Currency converter math (pure, unit-tested). Amounts are [BigDecimal], never floating point; results are
 * rounded half-up to 2 decimals. Rates are shekels per 1 unit of the trip currency.
 */
object CurrencyConversion {

    /** Longest whole part accepted in a converter field: up to 9,999,999.99. */
    const val MAX_INTEGER_DIGITS = 7

    private const val DECIMALS = 2

    /** What a field may contain while typing: "", "12", "12.", "12.5", "12,50", ".5". */
    private val PARTIAL_INPUT = Regex("""\d{0,$MAX_INTEGER_DIGITS}([.,]\d{0,$DECIMALS})?""")

    /** True when [text] is acceptable while typing (digits, one "." or ",", at most 2 decimals). */
    fun isValidInput(text: String): Boolean = PARTIAL_INPUT.matches(text)

    /** The amount in [text], or null when it is invalid or has no digits ("", "."). */
    fun parseAmount(text: String): BigDecimal? {
        if (!isValidInput(text) || text.none { it.isDigit() }) return null
        val normalized = text.replace(',', '.')
        return BigDecimal(if (normalized.startsWith('.')) "0$normalized" else normalized.removeSuffix("."))
    }

    /** [amount] of the trip currency in shekels: amount × rate, 2 decimals. */
    fun toIls(amount: BigDecimal, ilsPerUnit: BigDecimal): BigDecimal =
        amount.multiply(ilsPerUnit).setScale(DECIMALS, RoundingMode.HALF_UP)

    /** [amount] of shekels in the trip currency: amount ÷ rate, 2 decimals. */
    fun fromIls(amount: BigDecimal, ilsPerUnit: BigDecimal): BigDecimal =
        amount.divide(ilsPerUnit, DECIMALS, RoundingMode.HALF_UP)

    /** Converts the text of the [from] field into the text of the other field ("" when there is no amount). */
    fun convert(text: String, ilsPerUnit: BigDecimal, from: ConverterSide): String {
        val amount = parseAmount(text) ?: return ""
        val result = when (from) {
            ConverterSide.FOREIGN -> toIls(amount, ilsPerUnit)
            ConverterSide.ILS -> fromIls(amount, ilsPerUnit)
        }
        return formatAmount(result)
    }

    /** Field text for a computed amount, without grouping so it can be edited: "200", "12.5", "0.03". */
    fun formatAmount(amount: BigDecimal): String {
        val rounded = amount.setScale(DECIMALS, RoundingMode.HALF_UP)
        return if (rounded.signum() == 0) "0" else rounded.stripTrailingZeros().toPlainString()
    }

    /**
     * The user typed [text] into the [side] field. That field becomes the source and keeps [text] exactly as
     * typed; the other field is derived from it. Invalid text (letters, 3 decimals, more than
     * [MAX_INTEGER_DIGITS] digits) returns [input] unchanged, so the field keeps its previous value.
     */
    fun edit(input: ConverterInput, side: ConverterSide, text: String): ConverterInput =
        if (isValidInput(text)) input.copy(source = side, text = text) else input

    /** The swap button: moves the fields, both keep their amounts. */
    fun swap(input: ConverterInput): ConverterInput = input.copy(foreignOnLeft = !input.foreignOnLeft)

    /**
     * The largest of [sizes] (ordered largest first) for which [fits] is true, or the last (smallest) one when
     * none fits. Used to shrink the amount text until it is fully visible in its box.
     */
    fun <T> largestFittingSize(sizes: List<T>, fits: (T) -> Boolean): T =
        sizes.firstOrNull(fits) ?: sizes.last()

    /** Both field texts for [input] at [ilsPerUnit]. */
    fun converterState(input: ConverterInput, ilsPerUnit: BigDecimal): ConverterState {
        val other = convert(input.text, ilsPerUnit, input.source)
        return when (input.source) {
            ConverterSide.FOREIGN -> ConverterState(input.text, other, input.foreignOnLeft)
            ConverterSide.ILS -> ConverterState(other, input.text, input.foreignOnLeft)
        }
    }

    /**
     * Number part of "1 EUR = ₪3.44": 2 decimals for rates of ₪1 or more, otherwise 4 significant digits
     * ("0.01944"), so weak currencies don't show as "₪0.02".
     */
    fun formatRate(ilsPerUnit: BigDecimal, locale: Locale = AppLocale.locale): String {
        val rounded = if (ilsPerUnit >= BigDecimal.ONE) {
            ilsPerUnit.setScale(DECIMALS, RoundingMode.HALF_UP)
        } else {
            ilsPerUnit.round(MathContext(4, RoundingMode.HALF_UP)).stripTrailingZeros()
        }
        val digits = maxOf(DECIMALS, rounded.scale())
        val format = NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = true
            minimumFractionDigits = DECIMALS
            maximumFractionDigits = digits
        }
        return format.format(rounded)
    }

    /** The currency's symbol ("€", "$"), or null when the platform knows none besides the code itself. */
    fun currencySymbol(code: String, locale: Locale = AppLocale.locale): String? {
        val symbol = runCatching { java.util.Currency.getInstance(code).getSymbol(locale) }.getOrNull()
        return symbol?.takeIf { it != code }
    }
}
