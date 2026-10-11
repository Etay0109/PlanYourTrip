package com.example.planyourtrip.data.photo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoRulesTest {

    private fun image(name: String, width: Int = 2000, height: Int = 1333, mime: String? = null) =
        WikiImage(name, width, height, mime, url = "https://img.test/$name")

    // --- Not photos of the place --------------------------------------------------------------------------------

    @Test
    fun svgGifTiffAndPng_areNeverPhotos() {
        assertFalse(PhotoRules.isSuitable(image("Skyline.svg")))
        assertFalse(PhotoRules.isSuitable(image("Skyline.gif")))
        assertFalse(PhotoRules.isSuitable(image("Skyline.tif")))
        // Santorini's real lead image: a PNG map without a telling word in its name.
        assertFalse(PhotoRules.isSuitable(image("2011 Dimos Thiras.png")))
        // The service's mime wins over the name.
        assertFalse(PhotoRules.isSuitable(image("Skyline.jpg", mime = "image/svg+xml")))
    }

    @Test
    fun jpegAndWebp_arePhotos() {
        assertTrue(PhotoRules.isSuitable(image("London Skyline (125508655).jpeg")))
        assertTrue(PhotoRules.isSuitable(image("Petřín Tower View IMG 3020.JPG")))
        assertTrue(PhotoRules.isSuitable(image("Harbour.webp")))
        assertTrue(PhotoRules.isSuitable(image("No extension", mime = "image/jpeg")))
    }

    @Test
    fun flagsCoatsOfArmsMapsLogos_areRejectedByName() {
        listOf(
            "Flag of Canada.jpg", "Coat of arms of Prague.jpg", "Rome COA.jpg", "Italy Lazio locator map.jpg",
            "Lazio Location map.jpg", "Barcelona logo.jpg", "Emblem of Japan.jpg", "Seal of Boston.jpg",
            "Relief map of Spain.jpg", "Wappen Wien.jpg", "Escudo de Madrid.jpg", "Blason Paris.jpg",
            "Orthographic projection Greece.jpg", "EU-Germany_(orthographic_projection).jpg",
        ).forEach { assertFalse(it, PhotoRules.isSuitable(image(it))) }
    }

    @Test
    fun montagesAndCollages_areRejectedByName() {
        listOf(
            "Tokyo Montage 2015.jpg", "Collage of Paris.jpg", "Rome collage 2.jpg", "New_York_City_montage.jpg",
            "Berlin mosaic.jpg", "Lisbon composite.jpg",
        ).forEach { assertFalse(it, PhotoRules.isSuitable(image(it))) }
    }

    @Test
    fun hebrewNames_areFilteredToo() {
        listOf("דגל ישראל.jpg", "סמל ירושלים.jpg", "מפת אירופה.jpg", "מונטאז' תל אביב.jpg", "קולאז' חיפה.jpg")
            .forEach { assertFalse(it, PhotoRules.isSuitable(image(it))) }
        assertTrue(PhotoRules.isSuitable(image("נמל יפו.jpg")))
    }

    @Test
    fun blockedWords_matchWholeWordsOnly() {
        // Inside another word: fine.
        assertTrue(PhotoRules.isSuitable(image("Mapleton main street.jpg")))
        assertTrue(PhotoRules.isSuitable(image("Flagstaff downtown.jpg")))
        assertTrue(PhotoRules.isSuitable(image("Armstrong Park.jpg")))
        // A whole word: rejected, also when it's a place name (by design).
        assertFalse(PhotoRules.isSuitable(image("Seal_Beach_pier.jpg")))
    }

    // --- Size and shape ------------------------------------------------------------------------------------------

    @Test
    fun aspectRatio_from0_6To4_widePanoramasPass() {
        assertTrue(PhotoRules.isSuitable(image("Tall.jpg", 600, 1000)))
        assertTrue(PhotoRules.isSuitable(image("Panorama.jpg", 4000, 1000)))
        assertTrue(PhotoRules.isSuitable(image("London Ontario Skyline.jpg", 3852, 1961)))
        assertFalse(PhotoRules.isSuitable(image("Strip.jpg", 4500, 1000)))
        assertFalse(PhotoRules.isSuitable(image("Tower.jpg", 590, 1000)))
    }

    @Test
    fun tooSmallOrEmpty_isRejected() {
        assertFalse(PhotoRules.isSuitable(image("Small.jpg", 399, 300)))
        assertTrue(PhotoRules.isSuitable(image("Small.jpg", 400, 300)))
        assertFalse(PhotoRules.isSuitable(image("Broken.jpg", 1000, 0)))
    }

    @Test
    fun pageImages_onlyLandscapeJpegs_largestWins() {
        val best = PhotoRules.bestPageImage(
            listOf(
                image("Huge diagram.png", 8000, 5000, "image/png"),
                image("Portrait.jpg", 2000, 3000, "image/jpeg"),
                image("Square.jpg", 3000, 3000, "image/jpeg"),
                image("Webp.webp", 6000, 4000, "image/webp"),
                image("Old town.jpg", 1600, 1000, "image/jpeg"),
                image("Harbour.jpg", 3000, 2000, "image/jpeg"),
                image("Locator map.jpg", 9000, 6000, "image/jpeg"),
            )
        )
        assertEquals("Harbour.jpg", best?.fileName)
        assertNull(PhotoRules.bestPageImage(listOf(image("Portrait.jpg", 2000, 3000, "image/jpeg"))))
        assertNull(PhotoRules.bestPageImage(emptyList()))
    }

    // --- Distance -----------------------------------------------------------------------------------------------

    @Test
    fun distance_isGreatCircle() {
        // London ↔ London, Ontario ≈ 5,880 km; London ↔ Paris ≈ 344 km; a point to itself is 0.
        assertEquals(5_876.0, PhotoRules.distanceKm(51.507, -0.128, 42.985, -81.245), 10.0)
        assertEquals(344.0, PhotoRules.distanceKm(51.507, -0.128, 48.857, 2.352), 5.0)
        assertEquals(0.0, PhotoRules.distanceKm(10.0, 10.0, 10.0, 10.0), 1e-9)
    }

    @Test
    fun distanceLimit_dependsOnThePagesType() {
        assertEquals(PhotoRules.PLACE_KM, PhotoRules.maxDistanceKm(null), 0.0)
        assertEquals(PhotoRules.PLACE_KM, PhotoRules.maxDistanceKm("city"), 0.0)
        assertEquals(PhotoRules.PLACE_KM, PhotoRules.maxDistanceKm("landmark"), 0.0)
        assertEquals(PhotoRules.REGION_KM, PhotoRules.maxDistanceKm("isle"), 0.0)
        assertEquals(PhotoRules.REGION_KM, PhotoRules.maxDistanceKm("adm1st"), 0.0)
        assertEquals(PhotoRules.COUNTRY_KM, PhotoRules.maxDistanceKm("country"), 0.0)
        assertEquals(PhotoRules.COUNTRY_KM, PhotoRules.maxDistanceKm("Country"), 0.0)
    }

    @Test
    fun choosePage_withCoordinates_takesTheFirstCloseEnoughPage_inSearchOrder() {
        val pages = listOf(
            WikiPage("London, Ontario", index = 4, latitude = 42.985, longitude = -81.245, coordinateType = "city"),
            WikiPage("London", index = 1, latitude = 51.507, longitude = -0.128),
            WikiPage("Jack London", index = 5),
        )
        assertEquals("London, Ontario", PhotoRules.choosePage(pages, PhotoQuery("London", 42.983, -81.243))?.title)
        assertEquals("London", PhotoRules.choosePage(pages, PhotoQuery("London", 51.507, -0.128))?.title)
        // Nothing near Sydney.
        assertNull(PhotoRules.choosePage(pages, PhotoQuery("London", -33.87, 151.21)))
    }

    @Test
    fun choosePage_withoutCoordinates_takesTheFirstPage_skippingDisambiguation() {
        val pages = listOf(
            WikiPage("Georgia", index = 1, disambiguation = true),
            WikiPage("Georgia (country)", index = 2),
        )
        assertEquals("Georgia (country)", PhotoRules.choosePage(pages, PhotoQuery("Georgia"))?.title)
        assertNull(PhotoRules.choosePage(emptyList(), PhotoQuery("Nowhere")))
    }

    @Test
    fun choosePage_aCountryFarFromItsCentroid_isAccepted() {
        // Canada's Wikipedia point vs the suggestions' centroid: ~800 km, fine for a country, never for a city.
        val canada = WikiPage("Canada", index = 1, latitude = 60.0, longitude = -100.0, coordinateType = "country")
        assertEquals("Canada", PhotoRules.choosePage(listOf(canada), PhotoQuery("Canada", 61.07, -107.99))?.title)
        val city = canada.copy(coordinateType = "city")
        assertNull(PhotoRules.choosePage(listOf(city), PhotoQuery("Canada", 61.07, -107.99)))
    }

    // --- Keys ---------------------------------------------------------------------------------------------------

    @Test
    fun photoKey_trimsLowercasesAndRoundsCoordinates() {
        assertEquals("rome, italy|", photoKey("  Rome,   Italy ", null, null))
        assertEquals(photoKey("Rome", null, null), photoKey("ROME", null, null))
        assertEquals("london, canada|42.983,-81.243", photoKey("London, Canada", 42.9832406, -81.243372))
        // Within ~50 m: the same destination.
        assertEquals(photoKey("x", 42.98321, -81.24331), photoKey("x", 42.98324, -81.24336))
        // Picked and typed: different destinations.
        assertTrue(photoKey("London", 51.5, -0.12) != photoKey("London", null, null))
        // Only one coordinate counts as none.
        assertEquals("london|", photoKey("London", 51.5, null))
    }

    @Test
    fun hebrew_isDetected() {
        assertTrue(hasHebrew("פראג"))
        assertTrue(hasHebrew("Tel אביב"))
        assertFalse(hasHebrew("Prague"))
        assertFalse(hasHebrew("Αθήνα"))
    }
}
