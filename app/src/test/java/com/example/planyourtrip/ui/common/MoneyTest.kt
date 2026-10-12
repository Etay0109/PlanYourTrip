package com.example.planyourtrip.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MoneyTest {

    private val en = Locale.US

    @Test
    fun formatAgorot_wholeShekelsHaveNoDecimals() {
        assertEquals("2,550", Money.formatAgorot(255_000, en))
        assertEquals("0", Money.formatAgorot(0, en))
        assertEquals("1,000,000", Money.formatAgorot(100_000_000, en))
    }

    @Test
    fun formatAgorot_showsTwoDecimalsWhenThereAreAgorot() {
        assertEquals("12.50", Money.formatAgorot(1_250, en))
        assertEquals("0.05", Money.formatAgorot(5, en))
    }

    @Test
    fun formatAgorot_negativeUsesAbsoluteValue() {
        assertEquals("450", Money.formatAgorot(-45_000, en))
    }

    @Test
    fun formatWholeShekels_roundsToWholeShekelsWithoutDecimals() {
        assertEquals("1,792", Money.formatWholeShekels(179_241, en))
        assertEquals("1,792", Money.formatWholeShekels(179_249, en))
        assertEquals("1,793", Money.formatWholeShekels(179_250, en))
        assertEquals("1,793", Money.formatWholeShekels(179_299, en))
        assertEquals("2,550", Money.formatWholeShekels(255_000, en))
        assertEquals("1,000,000", Money.formatWholeShekels(100_000_000, en))
    }

    @Test
    fun formatWholeShekels_smallAndNegativeAmounts() {
        assertEquals("0", Money.formatWholeShekels(0, en))
        assertEquals("0", Money.formatWholeShekels(49, en))
        assertEquals("1", Money.formatWholeShekels(50, en))
        assertEquals("1,792", Money.formatWholeShekels(-179_241, en))
    }

    @Test
    fun parseAgorot_acceptsDotOrCommaAndUpToTwoDecimals() {
        assertEquals(12_000L, Money.parseAgorot("120"))
        assertEquals(1_250L, Money.parseAgorot("12.5"))
        assertEquals(1_250L, Money.parseAgorot("12,50"))
        assertEquals(5L, Money.parseAgorot("0.05"))
        assertEquals(700L, Money.parseAgorot(" 007 "))
        assertEquals(0L, Money.parseAgorot("0"))
    }

    @Test
    fun parseAgorot_rejectsEverythingElse() {
        listOf("", " ", "abc", "-3", "+3", "1.234", "1,000.50", "1.", ".5", "1e3", "12 50", "₪12").forEach {
            assertNull("\"$it\" should not parse", Money.parseAgorot(it))
        }
    }

    @Test
    fun parseAgorot_rejectsAmountsAboveTheCap() {
        assertEquals(Money.MAX_AGOROT, Money.parseAgorot("1000000000"))
        assertNull(Money.parseAgorot("1000000000.01"))
        assertNull(Money.parseAgorot("99999999999999999999999"))
    }

    @Test
    fun toInput_roundTripsThroughParse() {
        assertEquals("120", Money.toInput(12_000))
        assertEquals("12.50", Money.toInput(1_250))
        assertEquals("0.05", Money.toInput(5))
        listOf(12_000L, 1_250L, 5L, 99L).forEach { assertEquals(it, Money.parseAgorot(Money.toInput(it))) }
    }

    @Test
    fun formatPercent_atMostOneDecimal() {
        assertEquals("57.5%", Money.formatPercent(57.5, en))
        assertEquals("100%", Money.formatPercent(100.0, en))
        assertEquals("0%", Money.formatPercent(0.0, en))
        assertEquals("33.3%", Money.formatPercent(100.0 / 3, en))
    }
}
