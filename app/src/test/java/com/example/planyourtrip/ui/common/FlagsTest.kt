package com.example.planyourtrip.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlagsTest {

    /**
     * Every currency the rates service (Frankfurter `/v1/currencies`) offered on 2026-10-11, i.e. every currency the
     * currency picker can list. When the service adds one, add it here and give it a flag or leave it out on purpose
     * ([CURRENCIES_WITHOUT_FLAG]).
     */
    private val offeredCurrencies = listOf(
        "AUD", "BRL", "CAD", "CHF", "CNY", "CZK", "DKK", "EUR", "GBP", "HKD", "HUF", "IDR", "ILS", "INR", "ISK",
        "JPY", "KRW", "MXN", "MYR", "NOK", "NZD", "PHP", "PLN", "RON", "SEK", "SGD", "THB", "TRY", "USD", "ZAR",
    )

    @Test
    fun countryFlag_isTwoRegionalIndicators() {
        assertEquals("🇬🇧", countryFlag("GB"))
        assertEquals("🇮🇱", countryFlag("IL"))
        assertEquals("🇯🇵", countryFlag("jp"))
        assertEquals("🇪🇺", countryFlag("EU"))
    }

    @Test
    fun countryFlag_isNullForAnythingButTwoLetters() {
        for (bad in listOf(null, "", "G", "GBR", "G1", "  ", "גב", "1A")) assertNull(bad, countryFlag(bad))
    }

    @Test
    fun theExampleCurrencies_haveTheirFlags() {
        assertEquals("🇺🇸", currencyFlag("USD"))
        assertEquals("🇬🇧", currencyFlag("GBP"))
        assertEquals("🇯🇵", currencyFlag("JPY"))
        assertEquals("🇮🇱", currencyFlag("ILS"))
        assertEquals("🇪🇺", currencyFlag("EUR"))
    }

    @Test
    fun everyOfferedCurrency_hasAFlag_orIsLeftWithoutOneOnPurpose() {
        for (code in offeredCurrencies) {
            assertTrue(
                "$code needs a flag or an entry in CURRENCIES_WITHOUT_FLAG",
                currencyFlag(code) != null || code in CURRENCIES_WITHOUT_FLAG,
            )
        }
        for (code in CURRENCIES_WITHOUT_FLAG) assertNull(code, currencyFlag(code))
    }

    @Test
    fun everyTableEntry_isARealCurrencyWithAValidFlag() {
        for (code in CURRENCIES_WITH_FLAG) {
            assertNotNull(code, java.util.Currency.getInstance(code))
            val flag = checkNotNull(currencyFlag(code)) { code }
            assertEquals(code, 4, flag.length) // two regional indicators, two UTF-16 units each
        }
    }

    @Test
    fun anUnknownCurrency_getsNoFlag() {
        for (code in listOf("XAU", "XDR", "BTC", "ABC", "", "eu")) assertNull(code, currencyFlag(code))
        assertNull(currencyFlag(null))
    }
}
