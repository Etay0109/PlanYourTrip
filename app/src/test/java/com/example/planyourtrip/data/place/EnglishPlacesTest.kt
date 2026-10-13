package com.example.planyourtrip.data.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** English input matched against the built-in table (the real one), so popular places show before Photon answers. */
class EnglishPlacesTest {

    private fun names(query: String) = EnglishPlaces.search(query).map { it.label }

    @Test
    fun par_findsParisFirst() {
        val found = names("par")
        assertEquals("Paris, France", found.first())
        assertTrue("Paraguay" in found)
    }

    @Test
    fun fromTwoLetters_anyCase_andTrimmed() {
        assertEquals("Paris, France", names("PA").let { found -> found.first { it.startsWith("Paris") } })
        assertEquals(names("par"), names("  Par "))
    }

    @Test
    fun aLaterWordOfTheName_matchesToo_afterTheNamesThatStartWithIt() {
        assertTrue("New York, United States" in names("york"))
        assertTrue("Tel Aviv, Israel" !in names("york"))
        // "Rome" itself before a place with Rome as a later word.
        assertEquals("Rome, Italy", names("rome").first())
    }

    @Test
    fun theMiddleOfAWord_neverMatches() {
        assertTrue(names("aris").none { it.startsWith("Paris") })
    }

    @Test
    fun theLabelAPickPutsInTheField_matchesItsPlace() {
        assertEquals(listOf("Paris, France"), names("paris, fr"))
    }

    @Test
    fun accentsAndDashes_dontMatter() {
        val table = listOf(
            HebrewPlace("א", emptyList(), PlaceSuggestion("Île-de-France", null, "France", "FR", 48.7, 2.5)),
            HebrewPlace("ב", emptyList(), PlaceSuggestion("São Paulo", null, "Brazil", "BR", -23.5, -46.6)),
        )
        assertEquals(listOf("Île-de-France"), EnglishPlaces.search("ile de", table).map { it.name })
        assertEquals(listOf("São Paulo"), EnglishPlaces.search("sao", table).map { it.name })
    }

    @Test
    fun exactName_first_thenTableOrder_andAtMostFive() {
        val table = listOf("Romeo", "Rome", "Old Rome", "Romania", "Romford", "Rome Hills", "Romsey").map {
            HebrewPlace(it, emptyList(), PlaceSuggestion(it, null, null, null, null, null))
        }
        val names = { q: String -> EnglishPlaces.search(q, table).map { it.name } }
        assertEquals(listOf("Rome", "Romeo", "Rome Hills", "Old Rome"), names("rome"))
        assertEquals(listOf("Romeo", "Rome", "Romania", "Romford", "Rome Hills"), names("rom"))
    }

    @Test
    fun everyTableEntry_isFoundByItsEnglishName() {
        for (place in HEBREW_PLACE_TABLE) {
            val name = place.suggestion.name
            assertTrue(name, EnglishPlaces.search(name, limit = HEBREW_PLACE_TABLE.size).contains(place.suggestion))
        }
    }

    @Test
    fun hebrewTextFindsNothingHere() {
        assertTrue(EnglishPlaces.search("לונדון").isEmpty())
    }
}
