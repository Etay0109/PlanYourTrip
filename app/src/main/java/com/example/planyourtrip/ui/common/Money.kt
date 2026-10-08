package com.example.planyourtrip.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.planyourtrip.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Money helpers (pure, unit-tested). Amounts are always whole agorot in a [Long]
 * (100 agorot = ₪1); floating point is used only to display percentages.
 */
object Money {

    /** Largest amount accepted from a text field: ₪1,000,000,000. Keeps sums far from overflow. */
    const val MAX_AGOROT: Long = 100_000_000_000L

    private val INPUT = Regex("""\d+([.,]\d{1,2})?""")

    /**
     * Number part of an amount, grouped for [locale] (the app locale by default): "2,550" for whole shekels, "12.50" when there are
     * agorot. Negative amounts are shown by their absolute value (callers say "over budget" instead).
     */
    fun formatAgorot(agorot: Long, locale: Locale = AppLocale.locale): String {
        val value = abs(agorot)
        val format = NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = true
            val digits = if (value % 100 == 0L) 0 else 2
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        return format.format(value.toBigDecimal().movePointLeft(2))
    }

    /**
     * Parses what the user typed into agorot: digits with an optional "." or "," and at most two decimals
     * ("120", "12.5", "12,50"). Returns null for anything else (signs, letters, grouping, three decimals)
     * or above [MAX_AGOROT]. "0" parses to 0; callers decide whether zero is allowed.
     */
    fun parseAgorot(text: String): Long? {
        val trimmed = text.trim()
        if (!INPUT.matches(trimmed)) return null
        val parts = trimmed.split('.', ',')
        val shekels = parts[0].trimStart('0').ifEmpty { "0" }
        if (shekels.length > 10) return null
        val agorot = parts.getOrNull(1)?.padEnd(2, '0') ?: "00"
        val total = shekels.toLong() * 100 + agorot.toLong()
        return total.takeIf { it <= MAX_AGOROT }
    }

    /** [parseAgorot] for amounts that must be greater than zero (budget, cost, payment); null otherwise. */
    fun parsePositiveAgorot(text: String): Long? = parseAgorot(text)?.takeIf { it > 0 }

    /** Agorot as text for an input field, without grouping: "120", "12.50". */
    fun toInput(agorot: Long): String =
        if (agorot % 100 == 0L) (agorot / 100).toString()
        else "%d.%02d".format(Locale.ROOT, agorot / 100, agorot % 100)

    /** "57.5%", "100%" — at most one decimal. */
    fun formatPercent(percent: Double, locale: Locale = AppLocale.locale): String {
        val format = NumberFormat.getPercentInstance(locale).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 1
        }
        return format.format(percent / 100)
    }
}

/** "₪2,550" */
@Composable
fun shekels(agorot: Long): String = stringResource(R.string.money_shekels, Money.formatAgorot(agorot))
