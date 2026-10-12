package com.example.planyourtrip

import com.example.planyourtrip.data.photo.DestinationPhoto
import com.example.planyourtrip.data.photo.DestinationPhotoDao
import com.example.planyourtrip.data.photo.DestinationPhotoEntity
import com.example.planyourtrip.data.photo.DestinationPhotoRepository
import com.example.planyourtrip.data.photo.DestinationPhotoSource
import com.example.planyourtrip.data.photo.PhotoLookupException
import com.example.planyourtrip.data.photo.PhotoOrigin
import com.example.planyourtrip.data.photo.PhotoQuery
import com.example.planyourtrip.data.photo.PhotoResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * In-memory [DestinationPhotoSource] (no network). [results] maps a query's text to its answer (anything else finds
 * nothing); [offline] throws like a missing connection, [unreadable] like a bad answer, [gate] makes it wait.
 * [queries] records every lookup that started.
 */
class FakePhotoSource(var results: Map<String, PhotoResult> = emptyMap()) : DestinationPhotoSource {
    var offline = false
    var unreadable = false
    var gate: CompletableDeferred<Unit>? = null
    val queries = mutableListOf<PhotoQuery>()

    override suspend fun find(query: PhotoQuery): PhotoResult {
        queries += query
        gate?.await()
        if (offline) throw IOException("No connection")
        if (unreadable) throw PhotoLookupException("Bad answer")
        return results[query.text] ?: PhotoResult.NotFound
    }

    companion object {
        fun found(name: String) = PhotoResult.Found(
            cardUrl = "https://img.test/$name-960.jpg",
            thumbUrl = "https://img.test/$name-250.jpg",
            filePageUrl = "https://commons.test/File:$name.jpg",
            origin = PhotoOrigin.LEAD,
            fileName = "$name.jpg",
            pageTitle = name,
        )
    }
}

/** In-memory [DestinationPhotoDao] with Room's behavior (upsert by key, a flow per key list). */
class FakeDestinationPhotoDao : DestinationPhotoDao {
    val rows = MutableStateFlow<Map<String, DestinationPhotoEntity>>(emptyMap())

    override fun observe(keys: List<String>): Flow<List<DestinationPhotoEntity>> =
        rows.map { all -> keys.mapNotNull { all[it] } }

    override suspend fun get(key: String): DestinationPhotoEntity? = rows.value[key]

    override suspend fun upsert(row: DestinationPhotoEntity) {
        rows.value = rows.value + (row.key to row)
    }

    override suspend fun deleteNotFound(): Int {
        val gone = rows.value.values.count { it.cardUrl == null }
        rows.value = rows.value.filterValues { it.cardUrl != null }
        return gone
    }
}

/** [DestinationPhotoRepository] for ViewModel tests: [photos] by key, every [request] recorded. */
class FakeDestinationPhotoRepository : DestinationPhotoRepository {
    val photos = MutableStateFlow<Map<String, DestinationPhoto>>(emptyMap())
    val requested = mutableListOf<PhotoQuery>()

    override fun observe(keys: Set<String>): Flow<Map<String, DestinationPhoto>> =
        photos.map { all -> all.filterKeys { it in keys } }

    override fun request(queries: Collection<PhotoQuery>) {
        requested += queries
    }
}
