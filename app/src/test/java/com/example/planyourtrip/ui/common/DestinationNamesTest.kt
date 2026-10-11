package com.example.planyourtrip.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationNamesTest {

    @Test
    fun aPickedCity_showsOnlyItsName() {
        assertEquals("London", shortDestination("London, United Kingdom", "GB"))
        assertEquals("Ko Samui", shortDestination("Ko Samui, Thailand", "TH"))
    }

    @Test
    fun aPickedCountry_withoutAComma_staysWhole() {
        assertEquals("Greece", shortDestination("Greece", "GR"))
    }

    @Test
    fun aNameWithAComma_isCutOnlyAtTheLastComma() {
        assertEquals("Washington, D.C.", shortDestination("Washington, D.C., United States", "US"))
    }

    @Test
    fun freeTextWithACommaButNoCountryCode_staysWhole() {
        assertEquals("London, United Kingdom", shortDestination("London, United Kingdom", null))
        assertEquals("Rome, Florence and Venice", shortDestination("Rome, Florence and Venice", null))
        assertEquals("Paris, France", shortDestination("Paris, France", "FRA")) // not a valid code
    }

    @Test
    fun hebrewFreeText_staysWhole() {
        assertEquals("לונדון, אנגליה", shortDestination("לונדון, אנגליה", null))
        assertEquals("טיול משפחתי", shortDestination("טיול משפחתי", null))
    }

    @Test
    fun aCommaWithoutASpace_orNothingBeforeIt_keepsTheFullText() {
        assertEquals("Rome,Italy", shortDestination("Rome,Italy", "IT"))
        assertEquals(", Italy", shortDestination(", Italy", "IT"))
    }
}
