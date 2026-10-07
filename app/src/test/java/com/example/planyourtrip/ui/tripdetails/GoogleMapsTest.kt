package com.example.planyourtrip.ui.tripdetails

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URLDecoder

class GoogleMapsTest {

    private val base = "https://www.google.com/maps/search/?api=1&query="

    @Test
    fun spacesAndCommas_arePercentEncoded() {
        assertEquals(base + "Rome%2C%20Italy", googleMapsSearchUrl("Rome, Italy"))
        assertEquals(base + "New%20York", googleMapsSearchUrl("New York"))
    }

    @Test
    fun surroundingSpaces_areTrimmed() {
        assertEquals(base + "Paris", googleMapsSearchUrl("  Paris \n"))
    }

    @Test
    fun hebrewLetters_areEncodedAsUtf8() {
        assertEquals(base + "%D7%AA%D7%9C%20%D7%90%D7%91%D7%99%D7%91", googleMapsSearchUrl("תל אביב"))
    }

    @Test
    fun specialCharacters_neverBreakTheQuery() {
        val url = googleMapsSearchUrl("Café & Bar #1 + 50%/? (CH)")
        assertEquals(base + "Caf%C3%A9%20%26%20Bar%20%231%20%2B%2050%25%2F%3F%20%28CH%29", url)
        // One query parameter that decodes back to the destination.
        val query = url.removePrefix(base)
        assertEquals("Café & Bar #1 + 50%/? (CH)", URLDecoder.decode(query, "UTF-8"))
    }

    @Test
    fun mixedHebrewAndEnglish_decodesBack() {
        val destination = "ירושלים, Israel"
        assertEquals(destination, URLDecoder.decode(googleMapsSearchUrl(destination).removePrefix(base), "UTF-8"))
    }

    // ---------- "Open in Maps" on a Plan activity card ----------

    @Test
    fun activity_searchesTheLocationInTheTripDestination() {
        assertEquals(base + "Colosseum%2C%20Rome%2C%20Italy", googleMapsActivityUrl(" Colosseum ", "Rome, Italy"))
    }

    @Test
    fun activity_leavesTheDestinationOut_whenTheLocationAlreadyNamesIt() {
        assertEquals(base + "Trevi%20Fountain%2C%20rome", googleMapsActivityUrl("Trevi Fountain, rome", "Rome"))
    }

    @Test
    fun activity_withoutADestination_searchesTheLocationOnly() {
        assertEquals(base + "Piazza%20Navona", googleMapsActivityUrl("Piazza Navona", "  "))
    }

    @Test
    fun activity_hebrewAndSpecialCharacters_decodeBackToOneQuery() {
        val url = googleMapsActivityUrl("שוק הכרמל & Co #2", "תל אביב")
        assertEquals("שוק הכרמל & Co #2, תל אביב", URLDecoder.decode(url.removePrefix(base), "UTF-8"))
    }
}
