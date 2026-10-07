package com.example.planyourtrip

import com.example.planyourtrip.data.document.DocumentFileStore
import com.example.planyourtrip.data.document.DocumentTooLargeException
import com.example.planyourtrip.data.document.PickedFile
import java.io.IOException

/**
 * In-memory [DocumentFileStore]: [picked] says what each picked uri is (name, type, size); copies are recorded in
 * [stored] (file name → bytes). A picked file whose real size ([actualSizes]) exceeds the limit fails like the real
 * copy; [failCopy] makes every copy fail with an IOException.
 */
class FakeDocumentFileStore : DocumentFileStore {
    val picked = mutableMapOf<String, PickedFile>()
    val actualSizes = mutableMapOf<String, Long>()
    val stored = mutableMapOf<String, Long>()
    var failCopy = false

    override suspend fun inspect(uri: String): PickedFile = picked[uri] ?: throw IOException("Unknown uri $uri")

    override suspend fun copyIn(uri: String, fileName: String, maxBytes: Long): Long {
        if (failCopy) throw IOException("Copy failed")
        val size = actualSizes[uri] ?: picked[uri]?.sizeBytes ?: 1_000
        if (size > maxBytes) throw DocumentTooLargeException()
        stored[fileName] = size
        return size
    }

    override suspend fun delete(fileName: String) {
        stored.remove(fileName)
    }

    override suspend fun contentUri(fileName: String): String? =
        if (fileName in stored) "content://test.fileprovider/documents/$fileName" else null

    override suspend fun sweep(keep: Set<String>) {
        stored.keys.retainAll(keep)
    }
}
