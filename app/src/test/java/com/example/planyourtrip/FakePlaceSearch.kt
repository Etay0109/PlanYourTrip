package com.example.planyourtrip

import com.example.planyourtrip.data.place.PlaceSearch
import com.example.planyourtrip.data.place.PlaceSuggestion
import kotlinx.coroutines.CompletableDeferred
import java.io.IOException

/**
 * In-memory [PlaceSearch] (no network). [results] maps a query to its places (anything else finds nothing);
 * [offline] makes every search throw like a missing connection, [gate] makes it wait. [queries] records every search
 * that started, [completed] those that returned.
 */
class FakePlaceSearch(var results: Map<String, List<PlaceSuggestion>> = emptyMap()) : PlaceSearch {
    var offline = false
    var gate: CompletableDeferred<Unit>? = null
    val queries = mutableListOf<String>()
    val completed = mutableListOf<String>()

    override suspend fun search(query: String): List<PlaceSuggestion> {
        queries += query
        gate?.await()
        if (offline) throw IOException("No connection")
        return results[query].orEmpty().also { completed += query }
    }

    companion object {
        val LONDON = PlaceSuggestion("London", "England", "United Kingdom", "GB", 51.5074456, -0.1277653)
        val LONDON_ONTARIO = PlaceSuggestion("London", "Ontario", "Canada", "CA", 42.9832406, -81.243372)
        val ROME = PlaceSuggestion("Rome", "Lazio", "Italy", "IT", 41.8933203, 12.4829321)
        val TOKYO = PlaceSuggestion("Tokyo", null, "Japan", "JP", 35.6768601, 139.7638947)
        val CAIRO = PlaceSuggestion("Cairo", null, "Egypt", "EG", 30.0443879, 31.2357257)
        val TEL_AVIV = PlaceSuggestion("Tel Aviv", "Tel-Aviv District", "Israel", "IL", 32.0852997, 34.7818064)
    }
}
