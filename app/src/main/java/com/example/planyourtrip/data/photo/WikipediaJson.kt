package com.example.planyourtrip.data.photo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parsers for the MediaWiki and Wikidata answers (pure, unit-tested; `formatversion=2`). Malformed JSON or an API
 * error object throws [PhotoLookupException]; a single unreadable page or file is skipped.
 */
object WikipediaJson {

    /** `generator=search` + coordinates, pageimages and pageprops. No results → empty. */
    fun parseSearch(json: String): List<WikiPage> =
        pages(json).mapNotNull { runCatching { page(it.jsonObject) }.getOrNull() }

    /** `prop=imageinfo` (by titles or `generator=images`): one [WikiImage] per file that has its info. */
    fun parseImageInfo(json: String): List<WikiImage> =
        pages(json).mapNotNull { runCatching { imageInfo(it.jsonObject) }.getOrNull() }

    /** Wikidata `wbgetclaims` for P18: the file name of the first claim that isn't deprecated, or null. */
    fun parseWikidataImage(json: String): String? {
        val root = parse(json)
        root["error"]?.let { throw PhotoLookupException("Wikidata error: $it") }
        val claims = root["claims"]?.jsonObject?.get("P18") as? JsonArray ?: return null
        return claims.asSequence()
            .map { it.jsonObject }
            .filter { it.text("rank") != "deprecated" }
            .firstNotNullOfOrNull { claim ->
                claim["mainsnak"]?.jsonObject?.get("datavalue")?.jsonObject?.text("value")
            }
    }

    private fun pages(json: String): List<JsonElement> {
        val root = parse(json)
        root["error"]?.let { throw PhotoLookupException("Wikipedia error: $it") }
        val query = root["query"] as? JsonObject ?: return emptyList()
        return (query["pages"] as? JsonArray).orEmpty()
    }

    private fun parse(json: String): JsonObject = try {
        Json.parseToJsonElement(json).jsonObject
    } catch (e: IllegalArgumentException) {
        // SerializationException (malformed JSON) and a root that isn't an object.
        throw PhotoLookupException("Unreadable answer", e)
    }

    private fun page(page: JsonObject): WikiPage? {
        val title = page.text("title") ?: return null
        val coordinates = (page["coordinates"] as? JsonArray)?.firstOrNull()?.jsonObject
        val props = page["pageprops"] as? JsonObject
        val lead = page.text("pageimage")?.let { name ->
            val original = page["original"] as? JsonObject
            val thumbnail = page["thumbnail"] as? JsonObject
            WikiImage(
                fileName = name.replace('_', ' '),
                width = original?.int("width") ?: thumbnail?.int("width") ?: 0,
                height = original?.int("height") ?: thumbnail?.int("height") ?: 0,
                url = thumbnail?.text("source") ?: original?.text("source"),
            )
        }
        return WikiPage(
            title = title,
            index = page.int("index") ?: Int.MAX_VALUE,
            latitude = coordinates?.double("lat"),
            longitude = coordinates?.double("lon"),
            coordinateType = coordinates?.text("type"),
            // pageprops.disambiguation is "" when set.
            disambiguation = props?.containsKey("disambiguation") == true,
            wikidataId = props?.text("wikibase_item"),
            leadImage = lead,
        )
    }

    private fun imageInfo(page: JsonObject): WikiImage? {
        // "File:Name.jpg", or the wiki's own namespace name ("קובץ:…").
        val name = page.text("title")?.substringAfter(':') ?: return null
        val info = (page["imageinfo"] as? JsonArray)?.firstOrNull()?.jsonObject ?: return null
        return WikiImage(
            fileName = name,
            width = info.int("width") ?: return null,
            height = info.int("height") ?: return null,
            mime = info.text("mime"),
            url = info.text("thumburl") ?: info.text("url"),
            descriptionUrl = info.text("descriptionurl"),
        )
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject.double(key: String): Double? =
        this[key]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() }

    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}
