package com.example.planyourtrip.ui.tripdetails.expense

import com.example.planyourtrip.data.currency.ExchangeRate
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ForeignAmount
import com.example.planyourtrip.ui.common.AppLocale
import com.example.planyourtrip.ui.common.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/** The amount field and the selected currency, as in the form. */
data class EnteredAmount(val text: String, val currencyCode: String)

/** What saving the amount would store, or why it can't be saved. */
sealed interface AmountResult {
    /** [agorot] is deducted from the budget; [original] is null for an amount entered in shekels. */
    data class Ok(val agorot: Long, val original: ForeignAmount?) : AmountResult

    data class Invalid(val error: ExpenseFieldError) : AmountResult
}

/** The live line under a foreign amount: "≈ ₪40.20 · 1 EUR = ₪4.02 · rate of 2 Oct". */
data class ConversionPreview(
    val agorot: Long,
    val currencyCode: String,
    val ilsPerUnit: BigDecimal,
    val rateDate: LocalDate,
)

/**
 * Expense amounts in shekels or in the trip currency (pure, unit-tested). Conversions use [BigDecimal] only:
 * shekels = amount × rate, rounded half-up to whole agorot, with no fees or manual adjustment.
 */
object ExpenseCurrency {

    /** Longest whole part accepted for a foreign amount. Larger results are rejected by [Money.MAX_AGOROT]. */
    private const val MAX_INTEGER_DIGITS = 10

    /** Decimals of [code] (JPY 0, EUR 2, KWD 3); 2 when the platform doesn't know it. */
    fun fractionDigits(code: String): Int =
        runCatching { java.util.Currency.getInstance(code).defaultFractionDigits }.getOrNull()
            ?.takeIf { it >= 0 } ?: 2

    /**
     * Parses a foreign amount: digits with an optional "." or "," and at most [digits] decimals (none for a
     * currency without decimals). Returns null for anything else. The result has exactly [digits] decimals.
     */
    fun parseAmount(text: String, digits: Int): BigDecimal? {
        val pattern = if (digits == 0) {
            Regex("""\d{1,$MAX_INTEGER_DIGITS}""")
        } else {
            Regex("""\d{1,$MAX_INTEGER_DIGITS}([.,]\d{1,$digits})?""")
        }
        val trimmed = text.trim()
        if (!pattern.matches(trimmed)) return null
        return BigDecimal(trimmed.replace(',', '.')).setScale(digits)
    }

    /** [amount] × [ilsPerUnit] in whole agorot, rounded half-up. */
    fun toAgorot(amount: BigDecimal, ilsPerUnit: BigDecimal): Long =
        amount.multiply(ilsPerUnit).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()

    /** How a stored expense appears in the form: its original amount and currency, or shekels. */
    fun entered(expense: Expense): EnteredAmount = expense.original?.let {
        EnteredAmount(it.amount.toPlainString(), it.currencyCode)
    } ?: EnteredAmount(Money.toInput(expense.amountAgorot), ILS)

    /**
     * The one rule for what is saved. When editing ([stored] not null) and neither the amount nor the currency
     * changed, the stored shekel amount and rate are kept, even if the rate has changed since. Otherwise a
     * shekel amount is taken as is, and a foreign amount is converted with [rate] (the latest cached rate of
     * that currency); without a rate it can't be saved yet.
     */
    fun resolveAmount(entered: EnteredAmount, rate: ExchangeRate?, stored: Expense? = null): AmountResult {
        if (entered.text.isBlank()) return AmountResult.Invalid(ExpenseFieldError.EMPTY_AMOUNT)
        if (stored != null && isUnchanged(entered, stored)) {
            return AmountResult.Ok(stored.amountAgorot, stored.original)
        }
        if (entered.currencyCode == ILS) {
            val agorot = Money.parseAgorot(entered.text)
            return if (agorot == null || agorot <= 0) AmountResult.Invalid(ExpenseFieldError.INVALID_AMOUNT)
            else AmountResult.Ok(agorot, null)
        }
        val amount = parseAmount(entered.text, fractionDigits(entered.currencyCode))
        if (amount == null || amount.signum() <= 0) return AmountResult.Invalid(ExpenseFieldError.INVALID_AMOUNT)
        if (rate == null || rate.code != entered.currencyCode) return AmountResult.Invalid(ExpenseFieldError.NO_RATE)
        val agorot = toAgorot(amount, rate.ilsPerUnit)
        if (agorot <= 0 || agorot > Money.MAX_AGOROT) return AmountResult.Invalid(ExpenseFieldError.INVALID_AMOUNT)
        return AmountResult.Ok(agorot, ForeignAmount(amount, entered.currencyCode, rate.ilsPerUnit, rate.date))
    }

    /** Same currency and the same amount as stored ("10" and "10.00" are the same amount). */
    private fun isUnchanged(entered: EnteredAmount, stored: Expense): Boolean {
        val original = stored.original
        return if (original == null) {
            entered.currencyCode == ILS && Money.parseAgorot(entered.text) == stored.amountAgorot
        } else {
            entered.currencyCode == original.currencyCode &&
                parseAmount(entered.text, fractionDigits(original.currencyCode))
                    ?.compareTo(original.amount) == 0
        }
    }

    /** The live conversion line for a valid foreign amount; null for shekels or while it can't be converted. */
    fun conversionPreview(entered: EnteredAmount, rate: ExchangeRate?, stored: Expense? = null): ConversionPreview? {
        val result = resolveAmount(entered, rate, stored) as? AmountResult.Ok ?: return null
        val original = result.original ?: return null
        return ConversionPreview(result.agorot, original.currencyCode, original.ilsPerUnit, original.rateDate)
    }

    /** "€10.00", "¥1,500" — the original amount with its currency's symbol and decimals. */
    fun formatOriginal(original: ForeignAmount, locale: Locale = AppLocale.locale): String {
        val currency = runCatching { java.util.Currency.getInstance(original.currencyCode) }.getOrNull()
            ?: return "${original.amount.toPlainString()} ${original.currencyCode}"
        val digits = fractionDigits(original.currencyCode)
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        return format.format(original.amount)
    }
}
