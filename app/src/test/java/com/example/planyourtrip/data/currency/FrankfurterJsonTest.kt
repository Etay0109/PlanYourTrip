package com.example.planyourtrip.data.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

class FrankfurterJsonTest {

    private fun rates(vararg entries: String) = entries.joinToString(",", "[", "]")

    private fun entry(quote: String, rate: String, base: String = "ILS", date: String = "2026-10-12") =
        """{"date":"$date","base":"$base","quote":"$quote","rate":$rate}"""

    @Test
    fun rates_invertsThePerShekelQuote_andKeepsTheServiceDate() {
        val (rate, date) = FrankfurterJson.parseRates(rates(entry("ARS", "495.52")), quote = "ARS")
        assertEquals(BigDecimal.ONE.divide(BigDecimal("495.52"), MathContext(12)).stripTrailingZeros(), rate)
        assertEquals("0.00201808201485", rate.toPlainString())
        assertEquals(LocalDate.of(2026, 10, 12), date)
    }

    @Test
    fun rates_strongCurrency_givesShekelsPerUnit() {
        val (rate, _) = FrankfurterJson.parseRates(rates(entry("EUR", "0.29125")), quote = "EUR")
        assertEquals("3.43347639485", rate.toPlainString())
    }

    @Test
    fun rates_tinyCurrencies_keepTwelveSignificantDigits() {
        val (vnd, _) = FrankfurterJson.parseRates(rates(entry("VND", "8446")), quote = "VND")
        assertEquals("0.000118399242245", vnd.toPlainString())
        val (irr, _) = FrankfurterJson.parseRates(rates(entry("IRR", "570097")), quote = "IRR")
        assertEquals("0.00000175408746231", irr.toPlainString())
        // 1,000,000 IRR is ₪1.75, not ₪0 or ₪2.
        assertEquals(0, BigDecimal("1.75408746231").compareTo(irr.multiply(BigDecimal(1_000_000))))
    }

    @Test
    fun rates_acceptExponentNumbers() {
        val (gold, _) = FrankfurterJson.parseRates(rates(entry("XAU", "7.9e-05")), quote = "XAU")
        assertEquals(0, BigDecimal("12658.2278481").compareTo(gold))
        val (tiny, _) = FrankfurterJson.parseRates(rates(entry("ABC", "2.0e-06")), quote = "ABC")
        assertEquals("500000", tiny.toPlainString())
        val (upper, _) = FrankfurterJson.parseRates(rates(entry("ABC", "4E+2")), quote = "ABC")
        assertEquals("0.0025", upper.toPlainString())
    }

    @Test
    fun rates_picksTheAskedQuote_amongSeveral() {
        val json = rates(entry("EUR", "0.29125"), entry("ARS", "495.52", date = "2026-10-09"))
        val (rate, date) = FrankfurterJson.parseRates(json, quote = "ARS")
        assertEquals("0.00201808201485", rate.toPlainString())
        assertEquals(LocalDate.of(2026, 10, 9), date)
    }

    @Test
    fun rates_wrongBase_orWrongQuote_throws() {
        assertThrows(Exception::class.java) {
            FrankfurterJson.parseRates(rates(entry("ARS", "1418.4", base = "USD")), quote = "ARS")
        }
        assertThrows(Exception::class.java) {
            FrankfurterJson.parseRates(rates(entry("EUR", "0.29125")), quote = "ARS")
        }
        assertThrows(Exception::class.java) { FrankfurterJson.parseRates("[]", quote = "ARS") }
    }

    @Test
    fun rates_errorObject_badJson_zeroNegativeOrTextRate_throws() {
        assertThrows(Exception::class.java) {
            FrankfurterJson.parseRates("""{"status":422,"message":"invalid currency: ZZZ"}""", quote = "ZZZ")
        }
        assertThrows(Exception::class.java) { FrankfurterJson.parseRates("<html>Bad gateway</html>", "ARS") }
        assertThrows(Exception::class.java) { FrankfurterJson.parseRates(rates(entry("ARS", "0")), "ARS") }
        assertThrows(Exception::class.java) { FrankfurterJson.parseRates(rates(entry("ARS", "-495.52")), "ARS") }
        assertThrows(Exception::class.java) { FrankfurterJson.parseRates(rates(entry("ARS", "\"495.52\"")), "ARS") }
        assertThrows(Exception::class.java) { FrankfurterJson.parseRates(rates(entry("ARS", "null")), "ARS") }
        assertThrows(Exception::class.java) {
            FrankfurterJson.parseRates(rates(entry("ARS", "495.52", date = "12/10/2026")), "ARS")
        }
    }

    @Test
    fun currencies_areSortedByCode_withTheirNames() {
        val list = FrankfurterJson.parseCurrencies(
            """[{"iso_code":"USD","name":"United States Dollar","symbol":"$"},
               {"iso_code":"ARS","name":"Argentine Peso","symbol":"$","start_date":"1998-07-07"}]""",
        )
        assertEquals(listOf(Currency("ARS", "Argentine Peso"), Currency("USD", "United States Dollar")), list)
    }

    @Test
    fun currencies_leaveOutMetalsUnitsAndPoundsOfTheCrownDependencies() {
        val codes = listOf(
            "ARS", "XAU", "XAG", "XPD", "XPT", "XDR", "CMD", "CNH", "GGP", "IMP", "JEP", "XAF", "XOF", "XCD", "XPF",
            "XCG", "ANG", "CNY", "GBP",
        )
        val json = codes.joinToString(",", "[", "]") { """{"iso_code":"$it","name":"$it name"}""" }
        assertEquals(
            listOf("ANG", "ARS", "CNY", "GBP", "XAF", "XCD", "XCG", "XOF", "XPF"),
            FrankfurterJson.parseCurrencies(json).map { it.code },
        )
    }

    @Test
    fun travelCurrencies_filter() {
        for (code in listOf("ARS", "VND", "IRR", "XAF", "XCG", "ANG")) assertTrue(code, TravelCurrencies.isOffered(code))
        for (code in listOf("XAU", "XDR", "CNH", "JEP", "XYZ", "ars", "AR", "ARSS")) {
            assertFalse(code, TravelCurrencies.isOffered(code))
        }
    }

    @Test
    fun emptyCurrencyList_orNotAList_throws() {
        assertThrows(Exception::class.java) { FrankfurterJson.parseCurrencies("[]") }
        assertThrows(Exception::class.java) {
            FrankfurterJson.parseCurrencies("""[{"iso_code":"XAU","name":"Gold"}]""")
        }
        assertThrows(Exception::class.java) { FrankfurterJson.parseCurrencies("""{"USD":"United States Dollar"}""") }
    }
}
