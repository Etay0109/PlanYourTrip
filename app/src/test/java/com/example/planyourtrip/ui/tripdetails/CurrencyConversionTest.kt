package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.ui.tripdetails.CurrencyConversion.convert
import com.example.planyourtrip.ui.tripdetails.CurrencyConversion.formatRate
import com.example.planyourtrip.ui.tripdetails.CurrencyConversion.isValidInput
import com.example.planyourtrip.ui.tripdetails.CurrencyConversion.parseAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class CurrencyConversionTest {

    private val eur = BigDecimal("4.02")

    @Test
    fun input_acceptsUpToTwoDecimals_whileTyping() {
        listOf("", "0", "12", "12.", "12.5", "12.50", "12,5", ".5", ".").forEach {
            assertTrue("\"$it\" should be accepted", isValidInput(it))
        }
        listOf("12.345", "1.2.3", "-5", "1e5", "abc", "1 000", "12,50,1").forEach {
            assertFalse("\"$it\" should be rejected", isValidInput(it))
        }
    }

    @Test
    fun input_acceptsUpTo999999999999_99() {
        assertTrue(isValidInput("9999999.99"))
        assertTrue(isValidInput("9999999,99"))
        assertTrue(isValidInput("999999999999.99"))
        assertFalse(isValidInput("1000000000000"))
        assertFalse(isValidInput("1234567890123.9"))
    }

    @Test
    fun argentinePeso_convertsBothWays() {
        val ars = BigDecimal("0.00201808201485") // 1 ÷ 495.52, as FrankfurterJson stores it
        assertEquals("50.45", convert("25000", ars, ConverterSide.FOREIGN))
        assertEquals("123880", convert("250", ars, ConverterSide.ILS))
    }

    @Test
    fun tinyRate_iranianRial_staysExact_withLongAmounts() {
        val irr = BigDecimal("0.00000175408746231") // 1 ÷ 570097
        assertEquals("1", convert("570097", irr, ConverterSide.FOREIGN))
        assertEquals("570097000", convert("1000", irr, ConverterSide.ILS))
        assertEquals("1754087.46", convert("999999999999.99", irr, ConverterSide.FOREIGN))
        assertEquals("0.000001754", CurrencyConversion.formatRate(irr, Locale.US))
    }

    @Test
    fun emptyInput_convertsToEmpty() {
        assertNull(parseAmount(""))
        assertNull(parseAmount("."))
        assertEquals("", convert("", eur, ConverterSide.FOREIGN))
        assertEquals("", convert(".", eur, ConverterSide.ILS))
    }

    @Test
    fun partialInput_parses() {
        assertEquals(BigDecimal("12"), parseAmount("12."))
        assertEquals(BigDecimal("0.5"), parseAmount(".5"))
        assertEquals(BigDecimal("12.5"), parseAmount("12,5"))
    }

    @Test
    fun zero_convertsToZero() {
        assertEquals("0", convert("0", eur, ConverterSide.FOREIGN))
        assertEquals("0", convert("0.00", eur, ConverterSide.ILS))
    }

    @Test
    fun foreignToIls_multiplies() {
        assertEquals("201", convert("50", eur, ConverterSide.FOREIGN))
        assertEquals("50.25", convert("12.5", eur, ConverterSide.FOREIGN))
    }

    @Test
    fun ilsToForeign_divides_roundingHalfUp() {
        assertEquals("50", convert("201", eur, ConverterSide.ILS))
        // 100 / 4.02 = 24.8756… → 24.88
        assertEquals("24.88", convert("100", eur, ConverterSide.ILS))
        // 0.01 / 4.02 = 0.0024… → 0
        assertEquals("0", convert("0.01", eur, ConverterSide.ILS))
    }

    @Test
    fun swappingDirection_roundTrips() {
        val ils = convert("75.5", eur, ConverterSide.FOREIGN)
        assertEquals("303.51", ils)
        assertEquals("75.5", convert(ils, eur, ConverterSide.ILS))
    }

    @Test
    fun veryLargeAmounts_stayExact() {
        assertEquals("40199999.96", convert("9999999.99", eur, ConverterSide.FOREIGN))
        assertEquals("2487562.19", convert("9999999.99", eur, ConverterSide.ILS))
        // Weak currencies: the computed side may exceed the input limit, still exact.
        assertEquals("514403291.67", convert("9999999.99", BigDecimal("0.01944"), ConverterSide.ILS))
    }

    // ---------- The reported bug: typing amounts of 3+ digits ----------
    // Before the fix the typed text came back through the ViewModel's flows (and could be rewritten) and the
    // boxes were too narrow. Typing now goes through edit(): the typed field must equal exactly what was typed
    // after every keystroke, and the other field must hold the full conversion.

    /** Types [text] one character at a time into [side], checking both fields after each keystroke. */
    private fun typeDigitByDigit(text: String, side: ConverterSide, rate: BigDecimal = eur): ConverterState {
        var input = ConverterInput()
        var state = CurrencyConversion.converterState(input, rate)
        for (end in 1..text.length) {
            val typed = text.substring(0, end)
            input = CurrencyConversion.edit(input, side, typed)
            state = CurrencyConversion.converterState(input, rate)
            val (own, other) = when (side) {
                ConverterSide.FOREIGN -> state.foreignText to state.ilsText
                ConverterSide.ILS -> state.ilsText to state.foreignText
            }
            assertEquals("typed field after \"$typed\"", typed, own)
            assertEquals("other field after \"$typed\"", convert(typed, rate, side), other)
        }
        return state
    }

    @Test
    fun typing3Digits_inEitherField() {
        assertEquals(ConverterState("123", "494.46"), typeDigitByDigit("123", ConverterSide.FOREIGN))
        assertEquals(ConverterState("30.6", "123"), typeDigitByDigit("123", ConverterSide.ILS))
    }

    @Test
    fun typing4Digits_inEitherField() {
        assertEquals(ConverterState("1234", "4960.68"), typeDigitByDigit("1234", ConverterSide.FOREIGN))
        assertEquals(ConverterState("306.97", "1234"), typeDigitByDigit("1234", ConverterSide.ILS))
    }

    @Test
    fun typing7Digits_inEitherField() {
        assertEquals(ConverterState("1234567", "4962959.34"), typeDigitByDigit("1234567", ConverterSide.FOREIGN))
        assertEquals(ConverterState("307106.22", "1234567"), typeDigitByDigit("1234567", ConverterSide.ILS))
    }

    @Test
    fun typingWithDecimals_keepsTheDotAndTrailingZero() {
        // "123." and "123.0" must stay as typed, not be reformatted to "123" while typing.
        assertEquals(ConverterState("123.05", "494.66"), typeDigitByDigit("123.05", ConverterSide.FOREIGN))
        assertEquals(ConverterState("2487562.19", "9999999.99"), typeDigitByDigit("9999999.99", ConverterSide.ILS))
        assertEquals("1234,5", typeDigitByDigit("1234,5", ConverterSide.FOREIGN).foreignText)
    }

    @Test
    fun typingPastTheLimit_isIgnored_andTheFieldKeepsItsValue() {
        val full = CurrencyConversion.edit(ConverterInput(), ConverterSide.FOREIGN, "999999999999")
        assertEquals(full, CurrencyConversion.edit(full, ConverterSide.FOREIGN, "9999999999999")) // 13th digit
        val decimals = CurrencyConversion.edit(ConverterInput(), ConverterSide.ILS, "12.34")
        assertEquals(decimals, CurrencyConversion.edit(decimals, ConverterSide.ILS, "12.345")) // 3rd decimal
        assertEquals(decimals, CurrencyConversion.edit(decimals, ConverterSide.ILS, "12.34a"))
    }

    @Test
    fun switchingFields_neverRewritesWhatWasTyped() {
        var input = CurrencyConversion.edit(ConverterInput(), ConverterSide.FOREIGN, "1234")
        assertEquals("4960.68", CurrencyConversion.converterState(input, eur).ilsText)
        // Now edit the shekel field: it keeps its new text, the trip-currency field follows it.
        input = CurrencyConversion.edit(input, ConverterSide.ILS, "4960.6")
        assertEquals(ConverterState("1233.98", "4960.6"), CurrencyConversion.converterState(input, eur))
    }

    @Test
    fun swap_movesTheFields_andKeepsBothAmounts() {
        val typed = CurrencyConversion.edit(ConverterInput(), ConverterSide.FOREIGN, "1234567.89")
        val swapped = CurrencyConversion.swap(typed)
        assertEquals(
            ConverterState("1234567.89", "4962962.92", foreignOnLeft = false),
            CurrencyConversion.converterState(swapped, eur),
        )
        assertEquals(typed, CurrencyConversion.swap(swapped))
    }

    @Test
    fun largestFittingSize_shrinksUntilItFits() {
        val sizes = (24 downTo 10).toList()
        // A model: each character is 0.6 × font size wide; the box is 80 wide.
        fun fits(text: String) = { size: Int -> text.length * size * 0.6 <= 80 }
        assertEquals(24, CurrencyConversion.largestFittingSize(sizes, fits("123")))
        assertEquals(19, CurrencyConversion.largestFittingSize(sizes, fits("1234567")))
        assertEquals(13, CurrencyConversion.largestFittingSize(sizes, fits("9999999.99")))
        assertEquals(10, CurrencyConversion.largestFittingSize(sizes, fits("514403291151.15"))) // smallest
    }

    @Test
    fun converterState_computesTheOtherField_andKeepsSide() {
        val fromForeign = CurrencyConversion.converterState(ConverterInput(ConverterSide.FOREIGN, "50"), eur)
        assertEquals(ConverterState("50", "201", foreignOnLeft = true), fromForeign)
        val fromIls = CurrencyConversion.converterState(
            ConverterInput(ConverterSide.ILS, "201", foreignOnLeft = false),
            eur,
        )
        assertEquals(ConverterState("50", "201", foreignOnLeft = false), fromIls)
    }

    @Test
    fun formatRate_twoDecimalsAboveOne_fourSignificantBelow() {
        assertEquals("4.02", formatRate(BigDecimal("4.0215"), Locale.US))
        assertEquals("4.00", formatRate(BigDecimal("4"), Locale.US))
        assertEquals("1,234.57", formatRate(BigDecimal("1234.5678"), Locale.US))
        assertEquals("0.01944", formatRate(BigDecimal("0.01944"), Locale.US))
        assertEquals("0.2457", formatRate(BigDecimal("0.245678"), Locale.US))
        assertEquals("0.50", formatRate(BigDecimal("0.5"), Locale.US))
    }

    @Test
    fun currencySymbol_isNullWhenOnlyTheCodeIsKnown() {
        assertEquals("€", CurrencyConversion.currencySymbol("EUR", Locale.US))
        assertNull(CurrencyConversion.currencySymbol("XYZ", Locale.US))
    }

    // --- The card's rate line: per unit, or per shekel below ₪0.10 ------------------------------------------------

    private fun line(ilsPerUnit: String) = CurrencyConversion.rateLine(BigDecimal(ilsPerUnit), Locale.US)

    @Test
    fun rateLine_switchesToPerShekel_belowTenAgorot() {
        assertEquals(RateLine.PerUnit("0.10"), line("0.10"))
        assertEquals(RateLine.PerUnit("0.10"), line("0.1000"))
        assertEquals(RateLine.PerShekel("10.01"), line("0.0999"))
        assertEquals(RateLine.PerUnit("1.00"), line("1"))
    }

    @Test
    fun rateLine_euro_staysPerUnit() {
        assertEquals(RateLine.PerUnit("3.43"), line("3.43347639485"))
        assertEquals(RateLine.PerUnit("3.44"), line("3.4408"))
    }

    @Test
    fun rateLine_argentinePeso_isPerShekel_withTwoDecimals() {
        // Stored as 1 ÷ 495.52 (FrankfurterJson); shown back as the service's number.
        assertEquals(RateLine.PerShekel("495.52"), line("0.00201808201485"))
    }

    @Test
    fun rateLine_japaneseYen_isPerShekel() {
        assertEquals(RateLine.PerShekel("51.44"), line("0.01944"))
        assertEquals(RateLine.PerShekel("39.81"), line("0.02512"))
    }

    @Test
    fun rateLine_dongAndRial_areWhole_withGrouping() {
        assertEquals(RateLine.PerShekel("8,446"), line("0.000118399242245"))
        assertEquals(RateLine.PerShekel("570,097"), line("0.00000175408746231"))
    }

    @Test
    fun unitsPerShekel_roundsHalfUp_andGoesWholeFromAThousand() {
        fun perShekel(units: String) = CurrencyConversion.formatUnitsPerShekel(
            BigDecimal.ONE.divide(BigDecimal(units), java.math.MathContext.DECIMAL64), Locale.US,
        )
        assertEquals("10.01", perShekel("10.005"))
        assertEquals("999.99", perShekel("999.994"))
        // Rounds to 1,000.00: shown whole, never "1,000.00".
        assertEquals("1,000", perShekel("999.996"))
        assertEquals("1,235", perShekel("1234.5"))
        assertEquals("1,234", perShekel("1234.49"))
    }
}
