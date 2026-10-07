package com.example.planyourtrip.data.document

/** Largest file a document may have: 20 MB. */
const val MAX_DOCUMENT_BYTES: Long = 20L * 1024 * 1024

const val PDF_MIME_TYPE = "application/pdf"

/** MIME types offered by the file picker: PDFs and images. */
val DOCUMENT_MIME_TYPES = arrayOf(PDF_MIME_TYPE, "image/*")

/** Why a picked file can't become a document. */
enum class DocumentFileError { UNSUPPORTED_TYPE, TOO_LARGE }

/** "PDF" or "Photo" badge on a document card. */
enum class DocumentKind { PDF, PHOTO }

/**
 * Checks a picked file before the form opens: only PDFs and images, at most [MAX_DOCUMENT_BYTES]. A null
 * [sizeBytes] (the provider doesn't say) passes here; the copy then stops at the limit.
 */
fun checkPickedFile(mimeType: String?, sizeBytes: Long?): DocumentFileError? = when {
    documentKind(mimeType) == null -> DocumentFileError.UNSUPPORTED_TYPE
    sizeBytes != null && sizeBytes > MAX_DOCUMENT_BYTES -> DocumentFileError.TOO_LARGE
    else -> null
}

/** PDF, image, or null for anything else. */
fun documentKind(mimeType: String?): DocumentKind? {
    val type = mimeType?.lowercase()?.substringBefore(';')?.trim() ?: return null
    return when {
        type == PDF_MIME_TYPE -> DocumentKind.PDF
        type.startsWith("image/") && type.length > "image/".length -> DocumentKind.PHOTO
        else -> null
    }
}

/** Extension of the stored copy, so the file name also says what it is. */
fun extensionFor(mimeType: String): String = when (mimeType.lowercase()) {
    PDF_MIME_TYPE -> "pdf"
    "image/jpeg", "image/jpg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    "image/gif" -> "gif"
    "image/heic" -> "heic"
    "image/heif" -> "heif"
    else -> "bin"
}

/** The picked file's name without its extension, as the suggested document name ("" when unknown). */
fun defaultDocumentName(displayName: String?): String {
    val name = displayName?.trim().orEmpty()
    val dot = name.lastIndexOf('.')
    return (if (dot > 0) name.substring(0, dot) else name).trim()
}
