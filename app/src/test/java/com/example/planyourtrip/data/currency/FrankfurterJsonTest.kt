package com.example.planyourtrip.data.currency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FrankfurterJsonTest {

    @Test
    fun latest_readsRateExactly_andTheServiceDate() {
        val (rate, date) = FrankfurterJson.parseLatest(
            """{"amount":1.0,"base":"EUR","date":"2026-10-02","rates":{"ILS":3.4408}}""",
            expectedBase = "EUR",
        )
        assertEquals(BigDecimal("3.4408"), rate)
        assertEquals(LocalDate.of(2026, 10, 2), date)
    }

    @Test
    fun latest_smallRate_keepsAllDigits() {
        val (rate, _) = FrankfurterJson.parseLatest(
            """{"amount":1.0,"base":"JPY","date":"2026-10-02","rates":{"ILS":0.01944}}""",
            expectedBase = "JPY",
        )
        assertEquals(BigDecimal("0.01944"), rate)
    }

    @Test
    fun latest_otherAmount_isDividedToOneUnit() {
        val (rate, _) = FrankfurterJson.parseLatest(
            """{"amount":10,"base":"EUR","date":"2026-10-02","rates":{"ILS":34.408}}""",
            expectedBase = "EUR",
        )
        assertEquals(0, BigDecimal("3.4408").compareTo(rate))
    }

    @Test
    fun latest_missingIls_orBadJson_orZero_throws() {
        assertThrows(Exception::class.java) {
            FrankfurterJson.parseLatest("""{"amount":1.0,"base":"EUR","date":"2026-10-02","rates":{"USD":1.1}}""", "EUR")
        }
        assertThrows(Exception::class.java) { FrankfurterJson.parseLatest("<html>Bad gateway</html>", "EUR") }
        assertThrows(Exception::class.java) {
            FrankfurterJson.parseLatest("""{"amount":1.0,"base":"EUR","date":"2026-10-02","rates":{"ILS":0}}""", "EUR")
        }
    }

    @Test
    fun currencies_areSortedByCode() {
        val list = FrankfurterJson.parseCurrencies("""{"USD":"United States Dollar","AUD":"Australian Dollar"}""")
        assertEquals(
            listOf(Currency("AUD", "Australian Dollar"), Currency("USD", "United States Dollar")),
            list,
        )
    }

    @Test
    fun latest_forAnotherBase_throws() {
        assertThrows(Exception::class.java) {
            FrankfurterJson.parseLatest(
                """{"amount":1.0,"base":"USD","date":"2026-10-02","rates":{"ILS":3.75}}""",
                expectedBase = "EUR",
            )
        }
    }

    @Test
    fun latest_wholeRate_staysPlain() {
        val (rate, _) = FrankfurterJson.parseLatest(
            """{"amount":1.0,"base":"KWD","date":"2026-10-02","rates":{"ILS":10.0}}""",
            expectedBase = "KWD",
        )
        // stripTrailingZeros gives 1E+1; storage and display use the plain form.
        assertEquals("10", rate.toPlainString())
    }

    @Test
    fun emptyCurrencyList_throws() {
        assertThrows(Exception::class.java) { FrankfurterJson.parseCurrencies("{}") }
    }
}
