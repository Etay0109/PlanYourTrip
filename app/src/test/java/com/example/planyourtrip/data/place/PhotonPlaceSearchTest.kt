package com.example.planyourtrip.data.place

import com.example.planyourtrip.data.http.RecordingHttpGet
import com.example.planyourtrip.data.http.appUserAgent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The request [PhotonPlaceSearch] makes (the answers' parsing is in PhotonJsonTest). */
class PhotonPlaceSearchTest {

    private val london = """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point",""" +
        """"coordinates":[-0.1277653,51.5074456]},"properties":{"name":"London","osm_key":"place",""" +
        """"osm_value":"city","country":"United Kingdom","countrycode":"GB"}}]}"""

    @Test
    fun search_encodesTheTrimmedText_andIdentifiesTheApp() = runTest {
        val http = RecordingHttpGet(london)

        val results = PhotonPlaceSearch(appVersion = "2.0", http = http).search("  Tel Aviv & Jaffa ")

        assertEquals(
            listOf("https://photon.komoot.io/api/?q=Tel+Aviv+%26+Jaffa&lang=en&limit=10&osm_tag=place"),
            http.urls,
        )
        assertEquals(listOf(appUserAgent("2.0")), http.userAgents)
        assertEquals(listOf("London"), results.map { it.name })
    }
}
