package com.example.planyourtrip.data.document

import com.example.planyourtrip.data.sync.TripCleanup
import com.example.planyourtrip.data.trip.TripDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

/** Rows in Room, files through [files]. It is also the sync's [TripCleanup]: a deleted trip loses its documents. */
class RoomDocumentRepository(
    private val documentDao: DocumentDao,
    private val tripDao: TripDao,
    private val files: DocumentFileStore,
    private val clock: Clock = Clock.systemUTC(),
) : DocumentRepository, TripCleanup {

    override fun observeDocuments(tripId: Long, ownerId: String): Flow<List<TripDocument>> =
        documentDao.observeForTrip(tripId, ownerId).map { list -> list.map { it.toTripDocument() } }

    override suspend fun addDocument(
        tripId: Long,
        ownerId: String,
        input: DocumentInput,
        sourceUri: String,
        mimeType: String,
    ): Long? {
        val tripUuid = tripDao.uuidOf(tripId, ownerId) ?: return null
        val clean = input.normalized()
        val uuid = UUID.randomUUID().toString()
        val fileName = "$uuid.${extensionFor(mimeType)}"
        val size = files.copyIn(sourceUri, fileName, MAX_DOCUMENT_BYTES)
        return try {
            documentDao.insert(
                DocumentEntity(
                    ownerId = ownerId,
                    tripUuid = tripUuid,
                    uuid = uuid,
                    name = clean.name,
                    type = clean.type,
                    detail = clean.detail,
                    fileName = fileName,
                    mimeType = mimeType,
                    sizeBytes = size,
                    createdAt = clock.millis(),
                )
            )
        } catch (e: Exception) {
            files.delete(fileName)
            throw e
        }
    }

    override suspend fun updateDocument(id: Long, ownerId: String, input: DocumentInput) {
        val clean = input.normalized()
        documentDao.update(id, ownerId, clean.name, clean.type, clean.detail)
    }

    override suspend fun deleteDocument(id: Long, ownerId: String) {
        val document = documentDao.byId(id, ownerId) ?: return
        documentDao.delete(id, ownerId)
        files.delete(document.fileName)
    }

    override suspend fun fileToView(id: Long, ownerId: String): ViewFile? {
        val document = documentDao.byId(id, ownerId) ?: return null
        val uri = files.contentUri(document.fileName) ?: return null
        return ViewFile(uri, document.mimeType)
    }

    override suspend fun deleteForTrip(tripUuid: String, ownerId: String) {
        for (document in documentDao.forTrip(tripUuid, ownerId)) {
            documentDao.delete(document.id, ownerId)
            files.delete(document.fileName)
        }
    }

    override suspend fun removeOrphans(ownerId: String) {
        for (document in documentDao.orphans(ownerId)) {
            documentDao.delete(document.id, ownerId)
            files.delete(document.fileName)
        }
    }

    override suspend fun sweepFiles() {
        files.sweep(keep = documentDao.allFileNames().toSet())
    }

    /** After a full sync this device has every trip of [ownerId]; documents of the others are removed. */
    override suspend fun afterFullSync(ownerId: String) = removeOrphans(ownerId)
}
