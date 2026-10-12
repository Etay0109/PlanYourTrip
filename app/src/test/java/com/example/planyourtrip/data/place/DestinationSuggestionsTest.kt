package com.example.planyourtrip.data.place

import com.example.planyourtrip.FakePlaceSearch
import com.example.planyourtrip.OFFERED_CURRENCIES
import com.example.planyourtrip.FakePlaceSearch.Companion.LONDON
import com.example.planyourtrip.FakePlaceSearch.Companion.LONDON_ONTARIO
import com.example.planyourtrip.FakePlaceSearch.Companion.TEL_AVIV
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationSuggestionsTest {

    private val table = listOf(
        HebrewPlace("לונדון", emptyList(), LONDON),
        HebrewPlace("תל אביב", listOf("תל אביב יפו"), TEL_AVIV),
    )
    private val search = FakePlaceSearch(
        mapOf("lond" to listOf(LONDON, LONDON_ONTARIO), "תל" to listOf(TEL_AVIV), "לונ" to emptyList()),
    )
    private val suggestions = DestinationSuggestions(search, table)

    @Test
    fun latinText_asksPhotonOnly() = runTest {
        assertEquals(listOf(LONDON, LONDON_ONTARIO), suggestions.suggest("lond"))
        assertEquals(listOf("lond"), search.queries)
    }

    @Test
    fun textIsTrimmed_andShorterThanTwoCharacters_isNotLookedUp() = runTest {
        assertTrue(suggestions.suggest(" l ").isEmpty())
        assertTrue(suggestions.suggest("ל").isEmpty())
        assertTrue(search.queries.isEmpty())
        suggestions.suggest("  lond ")
        assertEquals(listOf("lond"), search.queries)
    }

    @Test
    fun hebrewText_findsTheTablesPlacesAbroad_thenPhotonsPlacesInIsrael() = runTest {
        assertEquals(listOf(LONDON), suggestions.suggest("לונ"))
        // A place both in the table and on Photon shows once.
        assertEquals(listOf(TEL_AVIV), suggestions.suggest("תל"))
        assertEquals(listOf("לונ", "תל"), search.queries)
    }

    @Test
    fun offline_hebrewStillFindsTheTablesPlaces_latinFindsNothing_andNothingThrows() = runTest {
        search.offline = true
        assertEquals(listOf(LONDON), suggestions.suggest("לונדון"))
        assertTrue(suggestions.suggest("lond").isEmpty())
    }

    @Test
    fun aServiceError_isAnEmptyList() = runTest {
        val failing = DestinationSuggestions({ throw IllegalStateException("HTTP 500") }, table)
        assertTrue(failing.suggest("lond").isEmpty())
    }

    @Test
    fun atMostSixSuggestions() = runTest {
        val many = (1..10).map { LONDON.copy(region = "Region $it") }
        val big = DestinationSuggestions(FakePlaceSearch(mapOf("lo" to many)), table)
        assertEquals(DestinationSuggestions.MAX_SUGGESTIONS, big.suggest("lo").size)
    }

    @Test
    fun countryCurrency_comesFromThePlatform() {
        assertEquals("GBP", countryCurrency("GB"))
        assertEquals("EUR", countryCurrency("FR"))
        assertEquals("JPY", countryCurrency("JP"))
        assertEquals("ILS", countryCurrency("IL"))
        assertEquals(null, countryCurrency("AQ"))
        assertEquals(null, countryCurrency("??"))
    }

    @Test
    fun countryCurrency_argentina_andRecentChanges() {
        assertEquals("ARS", countryCurrency("AR"))
        assertEquals("EUR", countryCurrency("BG"))
        assertEquals("EUR", countryCurrency("HR"))
        assertEquals("VES", countryCurrency("VE"))
        assertEquals("XCG", countryCurrency("CW"))
        assertEquals("ZWG", countryCurrency("ZW"))
    }

    @Test
    fun everyCountry_withACurrency_getsAnOfferedOne() {
        // So picking any country preselects its currency (when the service offers it, which it then always does).
        for (country in java.util.Locale.getISOCountries()) {
            val code = countryCurrency(country) ?: continue
            assertTrue("$country → $code", code in OFFERED_CURRENCIES)
        }
    }
}
