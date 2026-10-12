package com.example.planyourtrip.ui.common

import com.example.planyourtrip.OFFERED_CURRENCIES
import com.example.planyourtrip.SERVICE_CURRENCIES
import com.example.planyourtrip.data.currency.TravelCurrencies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlagsTest {

    @Test
    fun theTravelFilter_leavesExactlyTheOfferedCurrencies() {
        assertEquals(OFFERED_CURRENCIES, SERVICE_CURRENCIES.filter { TravelCurrencies.isOffered(it) })
        assertEquals(165, SERVICE_CURRENCIES.size)
    }

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
        for (code in OFFERED_CURRENCIES) {
            assertTrue(
                "$code needs a flag or an entry in CURRENCIES_WITHOUT_FLAG",
                currencyFlag(code) != null || code in CURRENCIES_WITHOUT_FLAG,
            )
        }
        for (code in CURRENCIES_WITHOUT_FLAG) assertNull(code, currencyFlag(code))
    }

    @Test
    fun everyTableEntry_isARealCurrencyWithAValidFlag() {
        // Codes newer than the JVM's currency data (ZWG of 2024, …) are checked against the offered list instead.
        for (code in CURRENCIES_WITH_FLAG) {
            assertTrue(code, code in OFFERED_CURRENCIES || runCatching { java.util.Currency.getInstance(code) }.isSuccess)
            val flag = checkNotNull(currencyFlag(code)) { code }
            assertEquals(code, 4, flag.length) // two regional indicators, two UTF-16 units each
        }
    }

    @Test
    fun anUnknownCurrency_getsNoFlag() {
        for (code in listOf("XAU", "XDR", "BTC", "ABC", "", "eu")) assertNull(code, currencyFlag(code))
        assertNull(currencyFlag(null))
    }

    @Test
    fun argentinaAndTinyCurrencies_haveTheirFlags() {
        assertEquals("🇦🇷", currencyFlag("ARS"))
        assertEquals("🇻🇳", currencyFlag("VND"))
        assertEquals("🇮🇷", currencyFlag("IRR"))
        for (code in listOf("XAF", "XOF", "XCD", "XPF", "XCG", "ANG")) assertNull(code, currencyFlag(code))
    }

    @Test
    fun everyFlagCountry_isARealCountryCode_orTheEu() {
        val countries = java.util.Locale.getISOCountries().toSet() + "EU"
        for (code in CURRENCIES_WITH_FLAG) assertTrue(code, code.take(2) in countries || code == "EUR")
    }
}
