package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.ME
import com.example.planyourtrip.data.currency.ExchangeRate
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ForeignAmount
import com.example.planyourtrip.ui.tripdetails.expense.AmountResult
import com.example.planyourtrip.ui.tripdetails.expense.ConversionPreview
import com.example.planyourtrip.ui.tripdetails.expense.EnteredAmount
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseCurrency
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseFieldError
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpenseCurrencyTest {

    private val oct2 = LocalDate.of(2026, 10, 2)
    private val oct5 = LocalDate.of(2026, 10, 5)
    private val eur = ExchangeRate("EUR", BigDecimal("4.02"), oct2, Instant.EPOCH)
    private val jpy = ExchangeRate("JPY", BigDecimal("0.02512"), oct2, Instant.EPOCH)

    private fun ok(agorot: Long, original: ForeignAmount? = null) = AmountResult.Ok(agorot, original)
    private fun invalid(error: ExpenseFieldError) = AmountResult.Invalid(error)
    private fun resolve(text: String, code: String, rate: ExchangeRate? = null, stored: Expense? = null) =
        ExpenseCurrency.resolveAmount(EnteredAmount(text, code), rate, stored)

    // ---------- Parsing and rounding ----------

    @Test
    fun fractionDigits_followTheCurrency() {
        assertEquals(2, ExpenseCurrency.fractionDigits("EUR"))
        assertEquals(0, ExpenseCurrency.fractionDigits("JPY"))
        assertEquals(3, ExpenseCurrency.fractionDigits("KWD"))
        assertEquals(2, ExpenseCurrency.fractionDigits("XYZ")) // unknown
    }

    @Test
    fun parseAmount_respectsTheCurrencyDecimals() {
        assertEquals(BigDecimal("10.50"), ExpenseCurrency.parseAmount("10,5", 2))
        assertEquals(BigDecimal("1500"), ExpenseCurrency.parseAmount("1500", 0))
        assertNull(ExpenseCurrency.parseAmount("1500.5", 0)) // JPY has no decimals
        assertNull(ExpenseCurrency.parseAmount("1500.", 0))
        assertNull(ExpenseCurrency.parseAmount("1.234", 2))
        assertEquals(BigDecimal("1.234"), ExpenseCurrency.parseAmount("1.234", 3))
        assertNull(ExpenseCurrency.parseAmount("-5", 2))
        assertNull(ExpenseCurrency.parseAmount("1,000.00", 2)) // no grouping
        assertNull(ExpenseCurrency.parseAmount("", 2))
    }

    @Test
    fun toAgorot_roundsHalfUpToWholeAgorot() {
        assertEquals(4_020L, ExpenseCurrency.toAgorot(BigDecimal("10"), BigDecimal("4.02")))
        // 1500 × 0.02512 = 37.68 exactly
        assertEquals(3_768L, ExpenseCurrency.toAgorot(BigDecimal("1500"), BigDecimal("0.02512")))
        // 3 × 3.4408 = 10.3224 → ₪10.32;  5 × 3.4409 = 17.2045 → ₪17.20 (agorot 1720.45 rounds down)
        assertEquals(1_032L, ExpenseCurrency.toAgorot(BigDecimal("3"), BigDecimal("3.4408")))
        assertEquals(1_720L, ExpenseCurrency.toAgorot(BigDecimal("5"), BigDecimal("3.4409")))
        // 0.125 × 1 = ₪0.125 → half-up → 13 agorot
        assertEquals(13L, ExpenseCurrency.toAgorot(BigDecimal("0.125"), BigDecimal.ONE))
    }

    // ---------- What is saved ----------

    @Test
    fun shekelAmount_isTakenAsIs_withoutOriginal() {
        assertEquals(ok(8_550), resolve("85.5", ILS, rate = eur))
        assertEquals(invalid(ExpenseFieldError.EMPTY_AMOUNT), resolve(" ", ILS))
        assertEquals(invalid(ExpenseFieldError.INVALID_AMOUNT), resolve("0", ILS))
    }

    @Test
    fun foreignAmount_isConvertedWithTheRate_andRemembersIt() {
        assertEquals(
            ok(4_020, ForeignAmount(BigDecimal("10.00"), "EUR", BigDecimal("4.02"), oct2)),
            resolve("10", "EUR", eur),
        )
    }

    @Test
    fun currencyWithoutDecimals() {
        assertEquals(
            ok(3_768, ForeignAmount(BigDecimal("1500"), "JPY", BigDecimal("0.02512"), oct2)),
            resolve("1500", "JPY", jpy),
        )
        assertEquals(invalid(ExpenseFieldError.INVALID_AMOUNT), resolve("1500.50", "JPY", jpy))
    }

    @Test
    fun argentinePeso_isConvertedWithTheInvertedRate() {
        val ars = ExchangeRate("ARS", BigDecimal("0.00201808201485"), oct2, Instant.EPOCH)
        assertEquals(
            ok(10090, ForeignAmount(BigDecimal("50000.00"), "ARS", ars.ilsPerUnit, oct2)),
            resolve("50000", "ARS", ars),
        )
    }

    @Test
    fun tinyRates_vietnameseDongAndIranianRial() {
        val vnd = ExchangeRate("VND", BigDecimal("0.000118399242245"), oct2, Instant.EPOCH)
        assertEquals(
            ok(17760, ForeignAmount(BigDecimal("1500000"), "VND", vnd.ilsPerUnit, oct2)),
            resolve("1500000", "VND", vnd),
        )
        val irr = ExchangeRate("IRR", BigDecimal("0.00000175408746231"), oct2, Instant.EPOCH)
        assertEquals(1754L, (resolve("10000000", "IRR", irr) as AmountResult.Ok).agorot)
        // 300 rials are less than half an agora: not an amount.
        assertEquals(invalid(ExpenseFieldError.INVALID_AMOUNT), resolve("300", "IRR", irr))
    }

    @Test
    fun noCachedRate_cannotBeSavedInTheTripCurrency() {
        assertEquals(invalid(ExpenseFieldError.NO_RATE), resolve("10", "EUR", rate = null))
        // A rate of another currency doesn't count.
        assertEquals(invalid(ExpenseFieldError.NO_RATE), resolve("10", "EUR", rate = jpy))
        // Format errors are reported first.
        assertEquals(invalid(ExpenseFieldError.INVALID_AMOUNT), resolve("abc", "EUR", rate = null))
        assertNull(ExpenseCurrency.conversionPreview(EnteredAmount("10", "EUR"), null))
    }

    @Test
    fun tinyAmountsThatRoundToZero_areInvalid() {
        assertEquals(invalid(ExpenseFieldError.INVALID_AMOUNT), resolve("0.001", "KWD", ExchangeRate("KWD", BigDecimal("1"), oct2, Instant.EPOCH)))
    }

    // ---------- Editing ----------

    private val storedEur = Expense(
        5, 1, "Gelato", 4_020, ExpenseCategory.FOOD, oct2,
        ForeignAmount(BigDecimal("10.00"), "EUR", BigDecimal("4.02"), oct2),
        paidBy = ME, splitBetween = listOf(ME),
    )
    private val newerEur = ExchangeRate("EUR", BigDecimal("4.10"), oct5, Instant.EPOCH)

    @Test
    fun editingWithoutChangingTheAmount_keepsTheStoredRateAndShekels() {
        val entered = ExpenseCurrency.entered(storedEur)
        assertEquals(EnteredAmount("10.00", "EUR"), entered)
        assertEquals(ok(4_020, storedEur.original), ExpenseCurrency.resolveAmount(entered, newerEur, storedEur))
        // "10" is the same amount as "10.00".
        assertEquals(ok(4_020, storedEur.original), resolve("10", "EUR", newerEur, storedEur))
        // Kept even when there is no rate at all now.
        assertEquals(ok(4_020, storedEur.original), resolve("10.00", "EUR", null, storedEur))
        // The live line shows the stored rate and date.
        assertEquals(
            ConversionPreview(4_020, "EUR", BigDecimal("4.02"), oct2),
            ExpenseCurrency.conversionPreview(entered, newerEur, storedEur),
        )
    }

    @Test
    fun editingTheAmount_usesTheLatestRate() {
        assertEquals(
            ok(4_920, ForeignAmount(BigDecimal("12.00"), "EUR", BigDecimal("4.10"), oct5)),
            resolve("12", "EUR", newerEur, storedEur),
        )
    }

    @Test
    fun switchingTheCurrency_recalculates() {
        // EUR → ILS: the same text now means shekels, no original.
        assertEquals(ok(1_000), resolve("10.00", ILS, newerEur, storedEur))
        // ... and back to EUR: unchanged again, so the stored values come back.
        assertEquals(ok(4_020, storedEur.original), resolve("10.00", "EUR", newerEur, storedEur))

        // A shekel expense switched to EUR is converted with the latest rate.
        val storedIls = Expense(6, 1, "Taxi", 1_000, ExpenseCategory.TRANSPORT, oct2, paidBy = ME, splitBetween = listOf(ME))
        assertEquals(EnteredAmount("10", ILS), ExpenseCurrency.entered(storedIls))
        assertEquals(ok(1_000), resolve("10", ILS, newerEur, storedIls))
        assertEquals(
            ok(4_100, ForeignAmount(BigDecimal("10.00"), "EUR", BigDecimal("4.10"), oct5)),
            resolve("10", "EUR", newerEur, storedIls),
        )
    }

    // ---------- Display ----------

    @Test
    fun formatOriginal_usesTheCurrencySymbolAndDecimals() {
        assertEquals("€10.00", ExpenseCurrency.formatOriginal(storedEur.original!!, Locale.US))
        assertEquals(
            "¥1,500",
            ExpenseCurrency.formatOriginal(ForeignAmount(BigDecimal("1500"), "JPY", BigDecimal.ONE, oct2), Locale.US),
        )
    }
}
