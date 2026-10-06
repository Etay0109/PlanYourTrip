package com.example.planyourtrip.ui.common.share

const val PDF_MIME_TYPE = "application/pdf"

/** A created PDF, ready for the share sheet. [uri] is a content URI another app may read once it is granted. */
data class SharedPdf(val uri: String, val mimeType: String = PDF_MIME_TYPE)

/**
 * Turns [TripPdfContent] into a PDF file that can be shared. Implemented by AndroidTripPdfExporter; tests use
 * FakeTripPdfExporter. ViewModels only ever see this interface.
 */
interface TripPdfExporter {
    /** Creates the file named [fileName] off the main thread, replacing older shared PDFs. Throws on failure. */
    suspend fun export(content: TripPdfContent, fileName: String): SharedPdf
}
