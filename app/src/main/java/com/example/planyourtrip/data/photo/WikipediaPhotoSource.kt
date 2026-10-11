package com.example.planyourtrip.data.photo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One HTTP GET returning the body. Throws [IOException] on a connection failure or an HTTP error status. */
fun interface HttpGet {
    suspend fun get(url: String, userAgent: String): String
}

/**
 * Destination photos from Wikipedia and Wikidata (no API key). The only Wikipedia HTTP code.
 *
 * 1. Search Wikipedia (Hebrew first for text with Hebrew letters, then English) and pick the page
 *    ([PhotoRules.choosePage]: for a picked place only a page whose coordinates are close enough).
 * 2. Its lead image if it is a suitable photo; else the Wikidata item's image (P18) under the same rules; else the
 *    largest landscape JPEG on the page. Nothing suitable → [PhotoResult.NotFound].
 * 3. The chosen file's 250 px thumbnail and description page.
 *
 * Wikimedia serves only standard thumbnail widths, so the sizes are 960 (card, header) and 250 (thumbnail).
 */
class WikipediaPhotoSource(
    appVersion: String,
    private val http: HttpGet = UrlConnectionHttpGet(),
    /** Which page and file each destination got (`adb logcat -s DestinationPhoto`). */
    private val log: (String) -> Unit = {},
) : DestinationPhotoSource {

    private val userAgent = photoUserAgent(appVersion)

    override suspend fun find(query: PhotoQuery): PhotoResult {
        val languages = if (hasHebrew(query.text)) listOf("he", "en") else listOf("en")
        for (language in languages) {
            val found = findIn(language, query)
            if (found != null) {
                log("\"${query.text}\" → ${found.origin} ${found.fileName} ($language: ${found.pageTitle})")
                return found
            }
        }
        log("\"${query.text}\" → no photo")
        return PhotoResult.NotFound
    }

    private suspend fun findIn(language: String, query: PhotoQuery): PhotoResult.Found? {
        val pages = parse { WikipediaJson.parseSearch(get(searchUrl(language, query.text))) }
        val page = PhotoRules.choosePage(pages, query) ?: return null
        val (image, origin) = chooseImage(language, page) ?: return null
        val cardUrl = image.url ?: return null
        val small = parse {
            WikipediaJson.parseImageInfo(get(imageInfoUrl(language, image.fileName, THUMB_WIDTH)))
        }.firstOrNull()
        return PhotoResult.Found(
            cardUrl = cardUrl,
            thumbUrl = small?.url ?: cardUrl,
            filePageUrl = small?.descriptionUrl ?: image.descriptionUrl,
            origin = origin,
            fileName = image.fileName,
            pageTitle = page.title,
        )
    }

    private suspend fun chooseImage(language: String, page: WikiPage): Pair<WikiImage, PhotoOrigin>? {
        page.leadImage?.takeIf { it.url != null && PhotoRules.isSuitable(it) }?.let { return it to PhotoOrigin.LEAD }

        page.wikidataId?.let { item ->
            val fileName = parse { WikipediaJson.parseWikidataImage(get(wikidataImageUrl(item))) }
            if (fileName != null) {
                val image = parse {
                    WikipediaJson.parseImageInfo(get(imageInfoUrl(language, fileName, CARD_WIDTH)))
                }.firstOrNull()
                if (image?.url != null && PhotoRules.isSuitable(image)) return image to PhotoOrigin.WIKIDATA
            }
        }

        val pageImages = parse { WikipediaJson.parseImageInfo(get(pageImagesUrl(language, page.title))) }
        return PhotoRules.bestPageImage(pageImages)?.let { it to PhotoOrigin.PAGE_IMAGES }
    }

    private suspend fun get(url: String): String = http.get(url, userAgent)

    /** Runs a request and its parser: connection failures stay [IOException], anything unreadable is wrapped. */
    private inline fun <T> parse(block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        throw e
    } catch (e: PhotoLookupException) {
        throw e
    } catch (e: Exception) {
        throw PhotoLookupException("Unreadable answer", e)
    }

    private fun api(language: String) = "https://$language.wikipedia.org/w/api.php?action=query&format=json" +
        "&formatversion=2&redirects=1"

    private fun searchUrl(language: String, text: String) = api(language) +
        "&generator=search&gsrsearch=${encode(text)}&gsrlimit=$SEARCH_LIMIT" +
        "&prop=coordinates%7Cpageimages%7Cpageprops&coprimary=primary&coprop=type" +
        "&piprop=thumbnail%7Cname%7Coriginal&pithumbsize=$CARD_WIDTH&ppprop=disambiguation%7Cwikibase_item"

    private fun imageInfoUrl(language: String, fileName: String, width: Int) = api(language) +
        "&titles=${encode("File:$fileName")}&prop=imageinfo&iiprop=url%7Csize%7Cmime&iiurlwidth=$width"

    private fun pageImagesUrl(language: String, title: String) = api(language) +
        "&titles=${encode(title)}&generator=images&gimlimit=$PAGE_IMAGES_LIMIT" +
        "&prop=imageinfo&iiprop=url%7Csize%7Cmime&iiurlwidth=$CARD_WIDTH"

    private fun wikidataImageUrl(item: String) =
        "https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&entity=${encode(item)}&property=P18"

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8")

    companion object {
        const val CARD_WIDTH = 960
        const val THUMB_WIDTH = 250
        private const val SEARCH_LIMIT = 5
        private const val PAGE_IMAGES_LIMIT = 50
    }
}

/**
 * [HttpGet] with `HttpURLConnection` (10 s timeouts). Cancelling disconnects the running request, like
 * `PhotonPlaceSearch`.
 */
class UrlConnectionHttpGet(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) : HttpGet {
    override suspend fun get(url: String, userAgent: String): String = withContext(ioDispatcher) {
        val connection = URL(url).openConnection() as HttpURLConnection
        val body = async { read(connection, userAgent) }
        try {
            body.await()
        } catch (e: CancellationException) {
            // A blocked socket read ignores coroutine cancellation: closing the connection ends it right away.
            connection.disconnect()
            throw e
        }
    }

    private fun read(connection: HttpURLConnection, userAgent: String): String = try {
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", userAgent)
        val status = connection.responseCode
        if (status !in 200..299) throw IOException("HTTP $status")
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000
    }
}
