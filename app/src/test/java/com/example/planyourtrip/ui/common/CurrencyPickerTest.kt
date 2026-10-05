package com.example.planyourtrip.ui.common

import com.example.planyourtrip.data.currency.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CurrencyPickerTest {
    private val list = listOf(
        Currency("AUD", "Australian Dollar"),
        Currency("EUR", "Euro"),
        Currency("USD", "US Dollar"),
    )

    @Test
    fun filter_matchesCodeOrName_codePrefixFirst() {
        assertEquals(list, filterCurrencies(list, "  "))
        assertEquals(listOf("EUR"), filterCurrencies(list, "eu").map { it.code })
        assertEquals(listOf("USD", "AUD"), filterCurrencies(list, "u").map { it.code }.take(2))
        assertEquals(listOf("AUD", "USD"), filterCurrencies(list, "dollar").map { it.code })
        assertTrue(filterCurrencies(list, "zzz").isEmpty())
    }

    @Test
    fun pickerState_loadsFiltersAndFails() {
        val loading = CurrencyPickerState(selectedCode = null).withQuery("dollar").loading()
        assertTrue(loading.isLoading)
        val loaded = loading.loaded(list)
        assertEquals(listOf("AUD", "USD"), loaded.results.map { it.code }) // query applied to the new list
        val failed = loaded.loading().loaded(null)
        assertTrue(failed.failed)
        assertEquals(list, failed.currencies) // a failed retry keeps what was loaded
    }

    @Test
    fun currencyName_fromTheListFirst_thenThePlatform() {
        assertEquals("Euro", currencyName("EUR", list, Locale.US))
        assertEquals("Swiss Franc", currencyName("CHF", list, Locale.US))
        assertEquals(null, currencyName("XYZ", list, Locale.US))
    }
}
