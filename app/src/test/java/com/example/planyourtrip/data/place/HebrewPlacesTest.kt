package com.example.planyourtrip.data.place

import com.example.planyourtrip.FakePlaceSearch.Companion.LONDON
import com.example.planyourtrip.FakePlaceSearch.Companion.ROME
import com.example.planyourtrip.data.trip.TripPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HebrewPlacesTest {

    private val newYork = PlaceSuggestion("New York", "New York", "United States", "US", 40.7127, -74.006)
    private val table = listOf(
        HebrewPlace("לונדון", emptyList(), LONDON),
        HebrewPlace("רומא", emptyList(), ROME),
        HebrewPlace("ניו יורק", emptyList(), newYork),
    )

    private fun names(query: String, list: List<HebrewPlace> = table) = HebrewPlaces.search(query, list).map { it.name }

    @Test
    fun aWholeName_andTheStartOfAName_match() {
        assertEquals(listOf("London"), names("לונדון"))
        assertEquals(listOf("London"), names("לונ"))
        assertEquals(listOf("Rome"), names("רו"))
    }

    @Test
    fun aLaterWord_matches() {
        assertEquals(listOf("New York"), names("יורק"))
    }

    @Test
    fun finalLetters_niqqud_maqafAndGeresh_areIgnored() {
        assertEquals(listOf("London"), names("לונדונ"))
        assertEquals(listOf("London"), names("לוֹנְדוֹן"))
        assertEquals(listOf("New York"), names("ניו-יורק"))
        assertEquals(listOf("New York"), names("ניו־יורק"))
        assertEquals(HebrewPlaces.normalize("צ׳כיה"), HebrewPlaces.normalize("צ'כיה"))
        assertEquals("ניו יורק", HebrewPlaces.normalize("  ניו   יורק "))
    }

    @Test
    fun exactMatchesComeFirst_thenTableOrder() {
        val list = listOf(
            HebrewPlace("פריזמה", emptyList(), ROME),
            HebrewPlace("פריז", emptyList(), LONDON),
        )
        assertEquals(listOf("London", "Rome"), names("פריז", list))
        assertEquals(listOf("Rome", "London"), names("פרי", list))
    }

    @Test
    fun noMatch_orLatinText_findsNothing() {
        assertTrue(names("ברלין").isEmpty())
        assertTrue(names("london").isEmpty())
        assertTrue(names("").isEmpty())
    }

    @Test
    fun containsHebrew() {
        assertTrue(HebrewPlaces.containsHebrew("לונדון"))
        assertTrue(HebrewPlaces.containsHebrew("NYC ניו"))
        assertFalse(HebrewPlaces.containsHebrew("London"))
    }

    // ---------- The committed table ----------

    @Test
    fun theTable_findsTheExamples() {
        assertEquals("London, United Kingdom", HebrewPlaces.search("לונדון").first().label)
        assertEquals("Paris, France", HebrewPlaces.search("פריז").first().label)
        assertEquals("Rome, Italy", HebrewPlaces.search("רומא").first().label)
        assertEquals("US", HebrewPlaces.search("ניו יורק").first().countryCode)
        assertEquals("Greece", HebrewPlaces.search("יוון").first().label)
        assertEquals("GR", HebrewPlaces.search("כרתים").first().countryCode)
        assertEquals("IT", HebrewPlaces.search("טוסקנה").first().countryCode)
    }

    @Test
    fun theTable_hasNoTwoEntriesWithTheSameHebrewNameOrAlias() {
        val seen = mutableMapOf<String, String>()
        for (place in HEBREW_PLACE_TABLE) {
            for (name in place.names) {
                val key = HebrewPlaces.normalize(name)
                val other = seen.put(key, place.hebrew)
                assertTrue("\"$name\" is in \"$other\" and \"${place.hebrew}\"", other == null)
            }
        }
    }

    @Test
    fun everyEntry_isValid() {
        assertTrue(HEBREW_PLACE_TABLE.size > 400)
        for (place in HEBREW_PLACE_TABLE) {
            val s = place.suggestion
            assertTrue(place.hebrew, place.names.all { HebrewPlaces.containsHebrew(it) || it.isNotBlank() })
            assertTrue(place.hebrew, HebrewPlaces.containsHebrew(place.hebrew))
            assertTrue(place.hebrew, s.name.isNotBlank() && !HebrewPlaces.containsHebrew(s.name))
            // Code and coordinates survive normalizing unchanged: valid code, both coordinates in range.
            assertEquals(place.hebrew, TripPlace(s.countryCode, s.latitude, s.longitude), s.toTripPlace())
            assertTrue(place.hebrew, s.countryCode != null && s.latitude != null)
        }
    }
}
