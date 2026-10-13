package com.example.planyourtrip.data.place

import com.example.planyourtrip.FakePlaceSearch
import com.example.planyourtrip.OFFERED_CURRENCIES
import com.example.planyourtrip.FakePlaceSearch.Companion.LONDON
import com.example.planyourtrip.FakePlaceSearch.Companion.LONDON_ONTARIO
import com.example.planyourtrip.FakePlaceSearch.Companion.TEL_AVIV
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun latinText_theTablesPlaceFirst_thenPhotons_once() = runTest {
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
    fun offline_theTablesPlacesStillShow_inHebrewAndEnglish_andNothingThrows() = runTest {
        search.offline = true
        assertEquals(listOf(LONDON), suggestions.suggest("לונדון"))
        assertEquals(listOf(LONDON), suggestions.suggest("lond"))
        assertTrue(suggestions.suggest("toky").isEmpty())
    }

    @Test
    fun aServiceError_isAnEmptyList() = runTest {
        val failing = DestinationSuggestions({ throw IllegalStateException("HTTP 500") }, table)
        assertTrue(failing.suggest("toky").isEmpty())
        assertNull(failing.remote("lond"))
    }

    @Test
    fun atMostSixSuggestions() = runTest {
        val many = (1..10).map { LONDON.copy(region = "Region $it", latitude = 40.0 + it) }
        val big = DestinationSuggestions(FakePlaceSearch(mapOf("lo" to many)), table)
        assertEquals(DestinationSuggestions.MAX_SUGGESTIONS, big.suggest("lo").size)
    }

    // ---------- Instant rows (no network) and merging ----------

    @Test
    fun instant_englishAndHebrew_fromTheTable_withoutAskingPhoton() {
        assertEquals(listOf(LONDON), suggestions.instant("lo"))
        assertEquals(listOf(TEL_AVIV), suggestions.instant("aviv"))
        assertEquals(listOf(LONDON), suggestions.instant("לונ"))
        assertTrue(suggestions.instant("l").isEmpty())
        assertTrue(suggestions.instant("toky").isEmpty())
        assertTrue(search.queries.isEmpty())
    }

    @Test
    fun merge_addsPhotonsPlacesUnderTheInstantOnes_withoutRepeats_andKeepsTheInstantRowsInPlace() {
        val paris = PlaceSuggestion("Paris", "Île-de-France", "France", "FR", 48.85, 2.35)
        val paraguay = PlaceSuggestion("Paraguay", null, "Paraguay", "PY", -23.3, -58.4)
        val parisTexas = PlaceSuggestion("Paris", "Texas", "United States", "US", 33.66, -95.55)
        // Photon's Paris: same place and country, another region line and slightly other coordinates.
        val photonParis = PlaceSuggestion("Paris", "Ile-de-France", "France", "FR", 48.8589, 2.32)
        val parma = PlaceSuggestion("Parma", "Emilia-Romagna", "Italy", "IT", 44.8, 10.33)

        val merged = DestinationSuggestions.merge(
            instant = listOf(paris, paraguay),
            remote = listOf(photonParis, parisTexas, parma, parisTexas.copy()),
        )
        assertEquals(listOf(paris, paraguay, parisTexas, parma), merged)
    }

    @Test
    fun merge_twoTownsOfOneNameInOneCountry_stayTwo() {
        val springfieldIllinois = PlaceSuggestion("Springfield", "Illinois", "United States", "US", 39.8, -89.65)
        val springfieldMissouri = PlaceSuggestion("Springfield", "Missouri", "United States", "US", 37.21, -93.29)
        assertEquals(
            listOf(springfieldIllinois, springfieldMissouri),
            DestinationSuggestions.merge(listOf(springfieldIllinois), listOf(springfieldMissouri)),
        )
    }

    @Test
    fun merge_aProvinceIsNotTheCityOfTheSameName() {
        val merged = DestinationSuggestions.merge(
            instant = listOf(FakePlaceSearch.BUENOS_AIRES),
            remote = listOf(FakePlaceSearch.BUENOS_AIRES_PROVINCE, FakePlaceSearch.BUENOS_AIRES),
        )
        assertEquals(listOf(FakePlaceSearch.BUENOS_AIRES, FakePlaceSearch.BUENOS_AIRES_PROVINCE), merged)
    }

    @Test
    fun merge_atMostSixRows_theInstantOnesAlwaysKept() {
        val instant = (1..5).map { LONDON.copy(name = "Place $it") }
        val remote = (1..10).map { LONDON.copy(name = "Remote $it") }
        val merged = DestinationSuggestions.merge(instant, remote)
        assertEquals(DestinationSuggestions.MAX_SUGGESTIONS, merged.size)
        assertEquals(instant, merged.take(5))
        assertEquals("Remote 1", merged.last().name)
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
