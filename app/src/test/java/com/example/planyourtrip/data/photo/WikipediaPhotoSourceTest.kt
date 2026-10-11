package com.example.planyourtrip.data.photo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.URI
import java.net.URLDecoder

/**
 * The Wikipedia lookup on recorded answers (`src/test/resources/wikipedia`, real responses saved once) and small
 * hand-written ones for the edge cases. No network.
 */
class WikipediaPhotoSourceTest {

    /** Answers by request; anything not set up answers "no results". Records every URL and User-Agent. */
    private class FakeWikipedia : HttpGet {
        private val answers = mutableMapOf<String, String>()
        val requests = mutableListOf<String>()
        val userAgents = mutableListOf<String>()
        var failure: IOException? = null

        fun search(language: String, text: String, body: String) = put("search:$language:$text", body)
        fun imageInfo(language: String, file: String, width: Int, body: String) =
            put("imageinfo:$language:$file:$width", body)
        fun wikidata(item: String, body: String) = put("wikidata:$item", body)
        fun pageImages(language: String, title: String, body: String) = put("pageimages:$language:$title", body)

        private fun put(key: String, body: String) {
            answers[key] = body
        }

        override suspend fun get(url: String, userAgent: String): String {
            requests += url
            userAgents += userAgent
            failure?.let { throw it }
            return answers[keyOf(url)] ?: """{"batchcomplete":true}"""
        }

        private fun keyOf(url: String): String {
            val uri = URI(url)
            val params = uri.rawQuery.split('&').associate {
                val (k, v) = it.split('=', limit = 2)
                k to URLDecoder.decode(v, "UTF-8")
            }
            val host = uri.host
            if (host == "www.wikidata.org") return "wikidata:${params["entity"]}"
            val language = host.substringBefore('.')
            return when {
                params["generator"] == "search" -> "search:$language:${params["gsrsearch"]}"
                params["generator"] == "images" -> "pageimages:$language:${params["titles"]}"
                else -> "imageinfo:$language:${params["titles"]?.removePrefix("File:")}:${params["iiurlwidth"]}"
            }
        }
    }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResource("wikipedia/$name")) { name }.readText()

    private val http = FakeWikipedia()
    private val logs = mutableListOf<String>()
    private val source = WikipediaPhotoSource("1.0", http, log = { logs += it })

    private suspend fun find(query: PhotoQuery): PhotoResult = source.find(query)

    private fun PhotoResult.found(): PhotoResult.Found =
        this as? PhotoResult.Found ?: throw AssertionError("expected a photo, got $this")

    // --- Picked places ------------------------------------------------------------------------------------------

    @Test
    fun pickedPlace_withAMatchingPage_usesItsLeadImage() = runTest {
        http.search("en", "London, Canada", resource("search_en_london_canada.json"))
        http.imageInfo("en", "London Ontario Skyline 2017 (cropped).jpg", 250, imageInfo(
            "London Ontario Skyline 2017 (cropped).jpg", 3852, 1961, "https://img.test/lo-250.jpg",
        ))

        val photo = find(PhotoQuery("London, Canada", 42.9832406, -81.243372)).found()

        assertEquals("London, Ontario", photo.pageTitle)
        assertEquals(PhotoOrigin.LEAD, photo.origin)
        assertTrue(photo.cardUrl, photo.cardUrl.contains("960px-London_Ontario_Skyline_2017"))
        assertEquals("https://img.test/lo-250.jpg", photo.thumbUrl)
        assertEquals("https://commons.test/File:London Ontario Skyline 2017 (cropped).jpg", photo.filePageUrl)
    }

    @Test
    fun aSameNamePageFarAway_isRejected_andTheRightOneFurtherDownIsTaken() = runTest {
        // "London" ranks London, Greater London and Chelsea (all in England) before London, Ontario.
        http.search("en", "London", resource("search_en_london.json"))

        val ontario = find(PhotoQuery("London", 42.9832406, -81.243372)).found()
        assertEquals("London, Ontario", ontario.pageTitle)
        assertTrue(ontario.cardUrl.contains("London_Ontario"))

        val england = find(PhotoQuery("London", 51.5074456, -0.1277653)).found()
        assertEquals("London", england.pageTitle)
        assertTrue(england.cardUrl.contains("London_Skyline"))
    }

    @Test
    fun aPickedPlace_withNoPageCloseEnough_hasNoPhoto() = runTest {
        http.search("en", "London", resource("search_en_london.json"))

        assertEquals(PhotoResult.NotFound, find(PhotoQuery("London", -33.87, 151.21)))
        // No image was looked at.
        assertEquals(1, http.requests.size)
        assertTrue(logs.single().contains("no photo"))
    }

    @Test
    fun aCountry_farFromItsCentroid_isAccepted_andItsFlagReplacedByTheWikidataImage() = runTest {
        http.search("en", "Canada", resource("search_en_canada.json"))
        http.wikidata("Q16", resource("wikidata_canada_p18.json"))
        val file = "NASA astronaut Scott Kelly took this aurora image over Canada on Jan. 21, 2016 01.jpg"
        http.imageInfo("en", file, 960, resource("imageinfo_en_canada_960.json"))
        http.imageInfo("en", file, 250, resource("imageinfo_en_canada_250.json"))

        val photo = find(PhotoQuery("Canada", 61.0666922, -107.991707)).found()

        assertEquals("Canada", photo.pageTitle)
        assertEquals(PhotoOrigin.WIKIDATA, photo.origin)
        assertEquals(file, photo.fileName)
        assertTrue(photo.cardUrl.contains("960px-"))
        assertTrue(photo.thumbUrl.contains("250px-"))
        assertTrue(logs.single().contains("WIKIDATA"))
    }

    // --- Free text ----------------------------------------------------------------------------------------------

    @Test
    fun hebrewFreeText_searchesHebrewWikipediaFirst_andSkipsItsMapForTheWikidataPhoto() = runTest {
        http.search("he", "פראג", resource("search_he_prague.json"))
        http.wikidata("Q1085", resource("wikidata_prague_p18.json"))
        http.imageInfo("he", "Petřín Tower View IMG 3020.JPG", 960, resource("imageinfo_he_prague_960.json"))
        http.imageInfo("he", "Petřín Tower View IMG 3020.JPG", 250, resource("imageinfo_he_prague_250.json"))

        val photo = find(PhotoQuery("פראג")).found()

        assertEquals("פראג", photo.pageTitle)
        assertEquals(PhotoOrigin.WIKIDATA, photo.origin)
        assertEquals("Petřín Tower View IMG 3020.JPG", photo.fileName)
        assertTrue(http.requests.first().startsWith("https://he.wikipedia.org/"))
        assertTrue(http.requests.none { it.startsWith("https://en.wikipedia.org/") })
    }

    @Test
    fun hebrewFreeText_thatHebrewWikipediaDoesntKnow_triesEnglish() = runTest {
        http.search("en", "קמבודיה", search(page("Cambodia", 1, lead = "Angkor Wat.jpg")))

        val photo = find(PhotoQuery("קמבודיה")).found()

        assertEquals("Cambodia", photo.pageTitle)
        assertTrue(http.requests[0].startsWith("https://he.wikipedia.org/"))
        assertTrue(http.requests[1].startsWith("https://en.wikipedia.org/"))
    }

    @Test
    fun englishFreeText_searchesEnglishOnly_andSkipsAPngMap() = runTest {
        // Santorini's lead image is a PNG map of the municipality: the Wikidata aerial photo is used.
        http.search("en", "Santorini", resource("search_en_santorini.json"))
        http.wikidata("Q129296", resource("wikidata_santorini_p18.json"))
        http.imageInfo("en", "Santoríni aerial.jpg", 960, resource("imageinfo_en_santorini_960.json"))
        http.imageInfo("en", "Santoríni aerial.jpg", 250, resource("imageinfo_en_santorini_250.json"))

        val photo = find(PhotoQuery("Santorini")).found()

        assertEquals(PhotoOrigin.WIKIDATA, photo.origin)
        assertEquals("Santoríni aerial.jpg", photo.fileName)
        assertTrue(http.requests.none { it.startsWith("https://he.wikipedia.org/") })
    }

    @Test
    fun aDisambiguationPage_isSkipped() = runTest {
        http.search("en", "Georgia", search(
            page("Georgia", 1, disambiguation = true, lead = "Georgia sunset.jpg"),
            page("Georgia (country)", 2, lead = "Tbilisi old town.jpg"),
        ))

        assertEquals("Georgia (country)", find(PhotoQuery("Georgia")).found().pageTitle)
    }

    @Test
    fun freeText_withNoResults_hasNoPhoto() = runTest {
        assertEquals(PhotoResult.NotFound, find(PhotoQuery("Qwxzv Plorb")))
    }

    // --- Fallbacks ----------------------------------------------------------------------------------------------

    @Test
    fun aMontageLeadImage_isReplacedByTheWikidataImage() = runTest {
        http.search("en", "Tokyo", search(page("Tokyo", 1, lead = "Tokyo Montage 2015.jpg", item = "Q1490")))
        http.wikidata("Q1490", wikidata("Shibuya crossing at night.jpg"))
        http.imageInfo("en", "Shibuya crossing at night.jpg", 960, imageInfo("Shibuya crossing at night.jpg", 4000, 2667))

        val photo = find(PhotoQuery("Tokyo")).found()

        assertEquals(PhotoOrigin.WIKIDATA, photo.origin)
        assertEquals("Shibuya crossing at night.jpg", photo.fileName)
    }

    @Test
    fun wikidataImageRejectedToo_takesTheLargestLandscapeJpegOfThePage() = runTest {
        http.search("en", "Ruritania", search(page("Ruritania", 1, lead = "Flag of Ruritania.svg", item = "Q9")))
        http.wikidata("Q9", wikidata("Collage of Ruritania.jpg"))
        http.imageInfo("en", "Collage of Ruritania.jpg", 960, imageInfo("Collage of Ruritania.jpg", 3000, 2000))
        http.pageImages("en", "Ruritania", imageInfoPages(
            Triple("Ruritania relief map.jpg", 9000, 6000),
            Triple("Parliament diagram.png", 8000, 5000),
            Triple("Castle portrait.jpg", 2000, 3000),
            Triple("Old town.jpg", 1600, 1000),
            Triple("Harbour at dawn.jpg", 3000, 2000),
        ))

        val photo = find(PhotoQuery("Ruritania")).found()

        assertEquals(PhotoOrigin.PAGE_IMAGES, photo.origin)
        assertEquals("Harbour at dawn.jpg", photo.fileName)
    }

    @Test
    fun noWikidataImage_goesStraightToThePageImages() = runTest {
        http.search("en", "Freedonia", search(page("Freedonia", 1, lead = "Freedonia locator map.jpg", item = "Q8")))
        http.wikidata("Q8", """{"claims":{}}""")
        http.pageImages("en", "Freedonia", imageInfoPages(Triple("Freedonia beach.jpg", 2400, 1600)))

        assertEquals("Freedonia beach.jpg", find(PhotoQuery("Freedonia")).found().fileName)
    }

    @Test
    fun nothingSuitableAnywhere_isNotFound() = runTest {
        http.search("en", "Atlantis", search(page("Atlantis", 1, lead = "Atlantis map.png", item = "Q7")))
        http.wikidata("Q7", wikidata("Coat of arms of Atlantis.jpg"))
        http.imageInfo("en", "Coat of arms of Atlantis.jpg", 960, imageInfo("Coat of arms of Atlantis.jpg", 1000, 1200))
        http.pageImages("en", "Atlantis", imageInfoPages(Triple("Atlantis portrait.jpg", 1000, 2000)))

        assertEquals(PhotoResult.NotFound, find(PhotoQuery("Atlantis")))
    }

    // --- Failures and politeness --------------------------------------------------------------------------------

    @Test
    fun aConnectionFailure_isAnIOException() = runTest {
        http.failure = IOException("HTTP 503")
        try {
            find(PhotoQuery("Rome"))
            fail("expected IOException")
        } catch (e: IOException) {
            assertEquals("HTTP 503", e.message)
        }
    }

    @Test
    fun anUnreadableAnswer_isAPhotoLookupException() = runTest {
        http.search("en", "Rome", "<html>Service unavailable</html>")
        try {
            find(PhotoQuery("Rome"))
            fail("expected PhotoLookupException")
        } catch (e: PhotoLookupException) {
            // expected
        }
        http.search("en", "Rome", """{"error":{"code":"maxlag"}}""")
        try {
            find(PhotoQuery("Rome"))
            fail("expected PhotoLookupException")
        } catch (e: PhotoLookupException) {
            // expected
        }
    }

    @Test
    fun everyRequest_sendsTheDescriptiveUserAgent() = runTest {
        http.search("en", "Canada", resource("search_en_canada.json"))
        http.wikidata("Q16", resource("wikidata_canada_p18.json"))
        find(PhotoQuery("Canada"))

        assertTrue(http.userAgents.size >= 2)
        http.userAgents.forEach {
            assertEquals("PlanYourTrip/1.0 (https://github.com/Etay0109/PlanYourTrip; Android; com.example.planyourtrip)", it)
        }
        assertFalse(http.userAgents.any { it.contains("@") })
    }

    // --- Hand-written answers -----------------------------------------------------------------------------------

    private fun page(
        title: String,
        index: Int,
        lead: String? = null,
        item: String? = null,
        disambiguation: Boolean = false,
    ): String {
        val props = listOfNotNull(
            if (disambiguation) "\"disambiguation\":\"\"" else null,
            item?.let { "\"wikibase_item\":\"$it\"" },
        ).joinToString(",")
        val image = lead?.let {
            val file = it.replace(' ', '_')
            """"pageimage":"$file","original":{"source":"https://img.test/$file","width":3000,"height":2000},""" +
                """"thumbnail":{"source":"https://img.test/960px-$file","width":960,"height":640},"""
        }.orEmpty()
        return """{"title":"$title","index":$index,$image"pageprops":{$props}}"""
    }

    private fun search(vararg pages: String) = """{"query":{"pages":[${pages.joinToString(",")}]}}"""

    private fun wikidata(file: String) =
        """{"claims":{"P18":[{"mainsnak":{"datavalue":{"value":"$file","type":"string"}},"rank":"normal"}]}}"""

    private fun imageInfoPage(file: String, width: Int, height: Int, url: String = "https://img.test/w-$file"): String {
        val mime = PhotoRules.mimeOf(file) ?: "image/jpeg"
        return """{"title":"File:$file","imageinfo":[{"width":$width,"height":$height,"mime":"$mime",""" +
            """"thumburl":"$url","descriptionurl":"https://commons.test/File:$file"}]}"""
    }

    private fun imageInfo(file: String, width: Int, height: Int, url: String = "https://img.test/w-$file") =
        """{"query":{"pages":[${imageInfoPage(file, width, height, url)}]}}"""

    private fun imageInfoPages(vararg files: Triple<String, Int, Int>) =
        """{"query":{"pages":[${files.joinToString(",") { (f, w, h) -> imageInfoPage(f, w, h) }}]}}"""
}
