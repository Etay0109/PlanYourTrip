package com.example.planyourtrip.data.photo

import com.example.planyourtrip.data.http.HttpGet
import com.example.planyourtrip.data.http.appUserAgent
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.URLEncoder

/**
 * Destination photos from Wikipedia and Wikidata (no API key). The only Wikipedia HTTP code.
 *
 * 1. Search Wikipedia (Hebrew first for text with Hebrew letters, then English) and pick the page
 *    ([PhotoRules.choosePage]: for a picked place only a page whose coordinates are close enough). When none is
 *    close enough, search again with the short name ([PhotoRules.fallbackName]) and take a page titled with it within
 *    400 km ([PhotoRules.chooseNamedNearby]): a province's point is often far from its page's point.
 * 2. Its lead image if it is a suitable landscape photo; a portrait lead (often a collage) only after the Wikidata
 *    item's image (P18, same rules); else the largest landscape JPEG on the page. Nothing suitable →
 *    [PhotoResult.NotFound]. Every rejected image is logged with the reason.
 * 3. The chosen file's 250 px thumbnail and description page.
 *
 * Wikimedia serves only standard thumbnail widths, so the sizes are 960 (card, header) and 250 (thumbnail).
 */
class WikipediaPhotoSource(
    appVersion: String,
    private val http: HttpGet,
    /** Which page and file each destination got (`adb logcat -s DestinationPhoto`). */
    private val log: (String) -> Unit = {},
) : DestinationPhotoSource {

    private val userAgent = appUserAgent(appVersion)

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
        val page = PhotoRules.choosePage(pages, query) ?: fallbackPage(language, query, pages) ?: return null
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

    /**
     * No page close enough: first a page of the [first] results titled with the whole name before the comma ("Buenos
     * Aires Province") within 400 km; then one more search with the short name (the [first] results again when the
     * short name is what was searched), for a page titled with it within 400 km.
     */
    private suspend fun fallbackPage(language: String, query: PhotoQuery, first: List<WikiPage>): WikiPage? {
        if (!query.hasCoordinates) return null
        val fullName = query.text.substringBefore(',').trim()
        if (fullName.length >= 2) {
            PhotoRules.chooseNamedNearby(first, query, fullName)?.let { page ->
                log("\"${query.text}\": no page close enough, took \"${page.title}\" ($language) within 400 km")
                return page
            }
        }
        val name = PhotoRules.fallbackName(query.text) ?: return null
        val pages = if (name.equals(query.text.trim(), ignoreCase = true)) first
        else parse { WikipediaJson.parseSearch(get(searchUrl(language, name))) }
        val page = PhotoRules.chooseNamedNearby(pages, query, name)
        log("\"${query.text}\": no page close enough, fallback search \"$name\" ($language) → ${page?.title ?: "none"}")
        return page
    }

    private suspend fun chooseImage(language: String, page: WikiPage): Pair<WikiImage, PhotoOrigin>? {
        val lead = page.leadImage?.takeIf { it.url != null && accept(it, "lead", page) }
        if (lead != null && !PhotoRules.isPortrait(lead)) return lead to PhotoOrigin.LEAD
        if (lead != null) log("Portrait lead ${lead.fileName} (${page.title}): trying the Wikidata image first")

        page.wikidataId?.let { item ->
            val fileName = parse { WikipediaJson.parseWikidataImage(get(wikidataImageUrl(item))) }
            if (fileName != null) {
                val image = parse {
                    WikipediaJson.parseImageInfo(get(imageInfoUrl(language, fileName, CARD_WIDTH)))
                }.firstOrNull()
                if (image?.url != null && accept(image, "Wikidata", page)) return image to PhotoOrigin.WIKIDATA
            }
        }
        if (lead != null) return lead to PhotoOrigin.LEAD

        val pageImages = parse { WikipediaJson.parseImageInfo(get(pageImagesUrl(language, page.title))) }
        val best = PhotoRules.bestPageImage(pageImages)
        if (best == null) log("No page image of ${page.title} passed (${pageImages.size} checked)")
        return best?.let { it to PhotoOrigin.PAGE_IMAGES }
    }

    /** True for a suitable image; otherwise logs why it was rejected. */
    private fun accept(image: WikiImage, step: String, page: WikiPage): Boolean {
        val reason = PhotoRules.rejection(image) ?: return true
        log("Rejected $step image ${image.fileName} (${page.title}): $reason")
        return false
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
