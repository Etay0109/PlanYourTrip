package com.example.planyourtrip.data.document

/** What the picked file is, read before anything is copied. */
data class PickedFile(val displayName: String?, val mimeType: String?, val sizeBytes: Long?)

/** The picked file turned out to be larger than allowed while copying it. */
class DocumentTooLargeException : Exception("The file is larger than the document limit")

/**
 * The documents' files in the app's private storage. Only [AndroidDocumentFileStore] touches them; ViewModels and
 * repositories see this interface, tests use a fake. Uris are passed as text, so callers need no Android types.
 */
interface DocumentFileStore {
    /** Name, type and size of a picked file (`content://` uri); fields the provider doesn't report are null. */
    suspend fun inspect(uri: String): PickedFile

    /**
     * Copies the picked file to [fileName] in the documents folder and returns the number of bytes copied. Throws
     * [DocumentTooLargeException] (and leaves nothing behind) once more than [maxBytes] were read, and an
     * IOException when the file can't be read or written.
     */
    suspend fun copyIn(uri: String, fileName: String, maxBytes: Long): Long

    /** Deletes a stored file; nothing happens when it doesn't exist. */
    suspend fun delete(fileName: String)

    /** A `content://` uri other apps may read (through the FileProvider), or null when the file is missing. */
    suspend fun contentUri(fileName: String): String?

    /** Deletes every stored file whose name isn't in [keep] (left behind by an interrupted copy). */
    suspend fun sweep(keep: Set<String>)
}
