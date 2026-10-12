package com.example.planyourtrip.data.place

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotonJsonTest {

    private fun feature(
        name: String?,
        value: String,
        country: String? = "United Kingdom",
        code: String? = "GB",
        state: String? = null,
        county: String? = null,
        key: String = "place",
        coordinates: String = "[-0.1277653, 51.5074456]",
    ): String {
        val props = listOfNotNull(
            name?.let { "\"name\":\"$it\"" }, "\"osm_key\":\"$key\"", "\"osm_value\":\"$value\"",
            country?.let { "\"country\":\"$it\"" }, code?.let { "\"countrycode\":\"$it\"" },
            state?.let { "\"state\":\"$it\"" }, county?.let { "\"county\":\"$it\"" },
        ).joinToString(",")
        return """{"type":"Feature","geometry":{"type":"Point","coordinates":$coordinates},"properties":{$props}}"""
    }

    private fun parse(vararg features: String) =
        PhotonJson.parse("""{"type":"FeatureCollection","features":[${features.joinToString(",")}]}""")

    @Test
    fun aCity_hasItsNameRegionCountryCodeAndCoordinates() {
        val london = parse(feature("London", "city", state = "England")).single()
        assertEquals("London", london.name)
        assertEquals("England", london.region)
        assertEquals("United Kingdom", london.countryName)
        assertEquals("GB", london.countryCode)
        assertEquals(51.5074456, london.latitude!!, 0.0)
        assertEquals(-0.1277653, london.longitude!!, 0.0)
        assertEquals("London, United Kingdom", london.label)
        assertEquals("England, United Kingdom", london.detail)
    }

    @Test
    fun aCountry_isJustItsName() {
        val greece = parse(feature("Greece", "country", country = "Greece", code = "GR", state = "Attica")).single()
        assertNull(greece.region)
        assertEquals("Greece", greece.label)
        assertNull(greece.detail)
    }

    @Test
    fun islandsAndRegions_areKept_streetsShopsAndHouses_areDropped() {
        val list = parse(
            feature("Santorini", "island", country = "Greece", code = "GR", state = "Aegean"),
            feature("Tuscany", "state", country = "Italy", code = "IT"),
            feature("London Ministore", "hairdresser", key = "shop"),
            feature("London Street", "residential", key = "highway"),
            feature("Mykonos 2", "house"),
            feature("London garden", "square"),
            feature("Soho", "neighbourhood"),
        )
        // Tuscany is an Italian region: it fills "Tuscany Region, Italy" (see the region tests below).
        assertEquals(listOf("Santorini, Greece", "Tuscany Region, Italy"), list.map { it.label })
        assertEquals("Aegean, Greece", list[0].detail)
        assertEquals("Italy", list[1].detail)
    }

    @Test
    fun aPlaceWithoutACountry_isItsNameWithoutAFlagCode() {
        val island = parse(feature("Point Nemo Island", "island", country = null, code = null)).single()
        assertEquals("Point Nemo Island", island.label)
        assertNull(island.countryCode)
        assertNull(island.detail)
    }

    @Test
    fun aBadCodeOrBadCoordinates_areDropped_notThePlace() {
        val list = parse(
            feature("A", "city", code = "GBR"),
            feature("B", "city", coordinates = "[200.0, 51.5]"),
            feature("C", "city", coordinates = "[0.0]"),
        )
        assertEquals(listOf("A", "B", "C"), list.map { it.name })
        assertNull(list[0].countryCode)
        assertTrue(list.drop(1).all { it.latitude == null && it.longitude == null })
    }

    @Test
    fun aFeatureWithoutAName_isSkipped() {
        assertEquals(listOf("London"), parse(feature(null, "city"), feature("London", "city")).map { it.name })
    }

    @Test
    fun twoPlacesWithTheSameName_inDifferentRegions_bothShow() {
        val list = parse(
            feature("London", "city", state = "England"),
            feature("London", "city", country = "Canada", code = "CA", state = "Ontario"),
        )
        assertEquals(listOf("England, United Kingdom", "Ontario, Canada"), list.map { it.detail })
    }

    @Test
    fun theSamePlaceTwice_showsOnce() {
        val list = parse(
            feature("Mykonos", "island", country = "Greece", code = "GR", state = "Aegean"),
            feature("Mykonos", "town", country = "Greece", code = "GR", state = "Aegean", coordinates = "[25.3, 37.4]"),
            feature("mykonos", "island", country = "greece", code = "GR", state = "aegean"),
        )
        // The first one stays.
        assertEquals(-0.1277653, list.single().longitude!!, 0.0)
    }

    @Test(expected = Exception::class)
    fun notJson_throws() {
        PhotonJson.parse("<html>Too many requests</html>")
    }

    // --- Regions never fill the same text as a city of the same name --------------------------------------------

    @Test
    fun buenosAires_theCityAndTheProvince_areTwoRows_withDifferentTexts() {
        val list = parse(
            feature("Buenos Aires", "city", country = "Argentina", code = "AR", state = "Autonomous City of Buenos Aires"),
            feature("Buenos Aires", "state", country = "Argentina", code = "AR"),
        )
        assertEquals(2, list.size)
        assertNull(list[0].regionKind)
        assertEquals(RegionKind.PROVINCE, list[1].regionKind)
        assertEquals(listOf("Buenos Aires, Argentina", "Buenos Aires Province, Argentina"), list.map { it.label })
        // The row itself still shows the plain name, with the "Province" label next to it.
        assertEquals("Buenos Aires", list[1].name)
        assertEquals("Argentina", list[1].detail)
    }

    @Test
    fun theRegionWord_followsTheCountry() {
        fun labelOf(name: String, value: String, country: String, code: String) =
            parse(feature(name, value, country = country, code = code)).single().label
        assertEquals("California State, United States", labelOf("California", "state", "United States", "US"))
        assertEquals("Bavaria State, Germany", labelOf("Bavaria", "state", "Germany", "DE"))
        assertEquals("Ontario Province, Canada", labelOf("Ontario", "province", "Canada", "CA"))
        assertEquals("Catalonia Region, Spain", labelOf("Catalonia", "state", "Spain", "ES"))
        assertEquals("Kent County, United States", labelOf("Kent", "county", "United States", "US"))
        // A name that already ends with the word keeps it once.
        assertEquals("Kent County, United States", labelOf("Kent County", "county", "United States", "US"))
        // England and Scotland are countries of their own: no word.
        assertEquals("England, United Kingdom", labelOf("England", "state", "United Kingdom", "GB"))
        assertNull(parse(feature("England", "state", country = "United Kingdom", code = "GB")).single().regionKind)
    }

    @Test
    fun citiesTownsAndIslands_haveNoRegionWord() {
        for (value in listOf("city", "town", "village", "municipality", "island", "archipelago", "country")) {
            assertNull(value, parse(feature("Place", value, country = "Argentina", code = "AR")).single().regionKind)
        }
    }
}
