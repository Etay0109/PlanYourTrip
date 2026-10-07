package com.example.planyourtrip

import com.example.planyourtrip.data.document.DocumentFileStore
import com.example.planyourtrip.data.document.DocumentInput
import com.example.planyourtrip.data.document.DocumentRepository
import com.example.planyourtrip.data.document.MAX_DOCUMENT_BYTES
import com.example.planyourtrip.data.document.TripDocument
import com.example.planyourtrip.data.document.ViewFile
import com.example.planyourtrip.data.document.extensionFor
import com.example.planyourtrip.data.document.normalized
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * In-memory [DocumentRepository] over a [FakeDocumentFileStore], with the same rules as RoomDocumentRepository:
 * owner scoping through [trips], the file is copied before the row is added, deleting removes the file.
 */
class FakeDocumentRepository(
    private val trips: FakeTripRepository,
    private val files: DocumentFileStore,
) : DocumentRepository {
    data class Row(val ownerId: String, val tripId: Long, val document: TripDocument)

    val rows = MutableStateFlow<List<Row>>(emptyList())

    /** Number of documents added, to check that a double tap saves once. */
    var adds = 0
        private set

    private fun owns(ownerId: String, tripId: Long) =
        trips.trips.value.any { (owner, trip) -> owner == ownerId && trip.id == tripId }

    override fun observeDocuments(tripId: Long, ownerId: String): Flow<List<TripDocument>> =
        combine(rows, trips.trips) { list, _ ->
            if (!owns(ownerId, tripId)) emptyList()
            else list.filter { it.ownerId == ownerId && it.tripId == tripId }.map { it.document }
        }

    override suspend fun addDocument(
        tripId: Long,
        ownerId: String,
        input: DocumentInput,
        sourceUri: String,
        mimeType: String,
    ): Long? {
        if (!owns(ownerId, tripId)) return null
        val id = (rows.value.maxOfOrNull { it.document.id } ?: 0) + 1
        val fileName = "doc$id.${extensionFor(mimeType)}"
        val size = files.copyIn(sourceUri, fileName, MAX_DOCUMENT_BYTES)
        val clean = input.normalized()
        adds++
        rows.update {
            it + Row(ownerId, tripId, TripDocument(id, clean.name, clean.type, clean.detail, fileName, mimeType, size, id))
        }
        return id
    }

    override suspend fun updateDocument(id: Long, ownerId: String, input: DocumentInput) {
        val clean = input.normalized()
        rows.update { list ->
            list.map {
                if (it.document.id == id && it.ownerId == ownerId) {
                    it.copy(document = it.document.copy(name = clean.name, type = clean.type, detail = clean.detail))
                } else {
                    it
                }
            }
        }
    }

    override suspend fun deleteDocument(id: Long, ownerId: String) {
        val row = rows.value.find { it.document.id == id && it.ownerId == ownerId } ?: return
        rows.update { it - row }
        files.delete(row.document.fileName)
    }

    override suspend fun fileToView(id: Long, ownerId: String): ViewFile? {
        val row = rows.value.find { it.document.id == id && it.ownerId == ownerId } ?: return null
        val uri = files.contentUri(row.document.fileName) ?: return null
        return ViewFile(uri, row.document.mimeType)
    }

    override suspend fun deleteForTrip(tripUuid: String, ownerId: String) {
        val gone = rows.value.filter { it.ownerId == ownerId && FakeTripRepository.uuidOf(it.tripId) == tripUuid }
        rows.update { it - gone.toSet() }
        gone.forEach { files.delete(it.document.fileName) }
    }

    override suspend fun removeOrphans(ownerId: String) {
        val orphans = rows.value.filter { it.ownerId == ownerId && !owns(ownerId, it.tripId) }
        rows.update { it - orphans.toSet() }
        orphans.forEach { files.delete(it.document.fileName) }
    }

    override suspend fun sweepFiles() = files.sweep(rows.value.map { it.document.fileName }.toSet())
}
