package com.example.planyourtrip.data.document

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * The documents' files in `filesDir/documents/` (private storage, not in the cache, so Android never clears them),
 * shared read-only through the FileProvider (`res/xml/file_paths.xml`). The only code that reads picked files or
 * touches the stored ones.
 */
class AndroidDocumentFileStore(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DocumentFileStore {

    private val dir: File get() = File(context.filesDir, DOCUMENTS_DIR)

    override suspend fun inspect(uri: String): PickedFile = withContext(ioDispatcher) {
        val parsed = Uri.parse(uri)
        val resolver = context.contentResolver
        var name: String? = null
        var size: Long? = null
        try {
            resolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the picked file's details", e)
        }
        PickedFile(displayName = name, mimeType = resolver.getType(parsed), sizeBytes = size)
    }

    override suspend fun copyIn(uri: String, fileName: String, maxBytes: Long): Long = withContext(ioDispatcher) {
        dir.mkdirs()
        val target = File(dir, fileName)
        try {
            val input = context.contentResolver.openInputStream(Uri.parse(uri))
                ?: throw IOException("Can't open the picked file")
            input.use { source ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) throw DocumentTooLargeException()
                        out.write(buffer, 0, read)
                    }
                    total
                }
            }
        } catch (e: Exception) {
            target.delete()
            Log.w(TAG, "Copying a document failed", e)
            throw e
        }
    }

    override suspend fun delete(fileName: String) {
        withContext(ioDispatcher) { File(dir, fileName).delete() }
    }

    override suspend fun contentUri(fileName: String): String? = withContext(ioDispatcher) {
        val file = File(dir, fileName)
        if (!file.isFile) return@withContext null
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
    }

    override suspend fun sweep(keep: Set<String>) {
        withContext(ioDispatcher) {
            dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
        }
    }

    private companion object {
        const val TAG = "TripDocuments"
        /** Must match the `files-path` in res/xml/file_paths.xml. */
        const val DOCUMENTS_DIR = "documents"
        const val BUFFER_SIZE = 64 * 1024
    }
}
