package com.example.planyourtrip.data.document

/**
 * What a travel document is; sets its icon. Stored by [name] in Room, so entries can be reordered or appended, but
 * never renamed or removed without a migration (an unknown name falls back to [OTHER]).
 */
enum class DocumentType {
    FLIGHT, LODGING, INSURANCE, TICKET, OTHER;

    companion object {
        fun fromName(name: String?): DocumentType = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** A travel document of a trip: one PDF or image file kept in the app's private storage on this device. */
data class TripDocument(
    val id: Long,
    val name: String,
    val type: DocumentType,
    /** Optional second line ("TLV → FCO · 19 Oct"); null when empty. */
    val detail: String?,
    /** File name inside the app's documents folder (see [DocumentFileStore]). */
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
)

/** A stored file shared read-only with the system viewer: a `content://` uri and its type. */
data class ViewFile(val uri: String, val mimeType: String)

/** What the document form saves (name, type, detail line). Texts are trimmed by the repository. */
data class DocumentInput(val name: String, val type: DocumentType, val detail: String?)

/** Trims the texts and turns a blank detail line into null, as stored. */
fun DocumentInput.normalized(): DocumentInput =
    copy(name = name.trim(), detail = detail?.trim()?.takeIf { it.isNotEmpty() })
