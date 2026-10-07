package com.example.planyourtrip.data.document

import kotlinx.coroutines.flow.Flow

/**
 * The travel documents of the owner's trips, on this device only. Callers pass the signed-in user's uid as
 * [ownerId]. Failures of the file copy are thrown ([DocumentTooLargeException], IOException).
 */
interface DocumentRepository {
    /** The trip's documents in the order they were added. Empty if the trip is not the owner's. */
    fun observeDocuments(tripId: Long, ownerId: String): Flow<List<TripDocument>>

    /**
     * Copies the picked file ([sourceUri], of [mimeType]) into private storage and adds the document. Returns its
     * id, or null when the trip is not the owner's (nothing is copied then).
     */
    suspend fun addDocument(tripId: Long, ownerId: String, input: DocumentInput, sourceUri: String, mimeType: String): Long?

    /** Changes name, type and detail line (the file stays). */
    suspend fun updateDocument(id: Long, ownerId: String, input: DocumentInput)

    /** Deletes the document and its file. */
    suspend fun deleteDocument(id: Long, ownerId: String)

    /** The stored file as other apps may open it, or null when the document or its file is missing. */
    suspend fun fileToView(id: Long, ownerId: String): ViewFile?

    /**
     * Deletes the documents (and files) of the owner's trip [tripUuid], after that trip was deleted. Only that trip:
     * [removeOrphans] would also remove documents whose trip hasn't come back from the cloud yet after a sign-in.
     */
    suspend fun deleteForTrip(tripUuid: String, ownerId: String)

    /** Deletes the owner's documents (and files) whose trip is no longer on this device for that owner. */
    suspend fun removeOrphans(ownerId: String)

    /** Deletes stored files that belong to no document (an interrupted copy). */
    suspend fun sweepFiles()
}
