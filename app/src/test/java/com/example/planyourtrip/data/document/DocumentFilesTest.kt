package com.example.planyourtrip.data.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentFilesTest {
    @Test
    fun pdfAndImages_areAccepted() {
        assertNull(checkPickedFile("application/pdf", 1_000))
        assertNull(checkPickedFile("image/jpeg", 1_000))
        assertNull(checkPickedFile("image/png", 1_000))
        assertNull(checkPickedFile("image/heic", 1_000))
        assertNull(checkPickedFile("APPLICATION/PDF", 1_000))
    }

    @Test
    fun otherTypes_areUnsupported() {
        assertEquals(DocumentFileError.UNSUPPORTED_TYPE, checkPickedFile("text/plain", 10))
        assertEquals(DocumentFileError.UNSUPPORTED_TYPE, checkPickedFile("application/zip", 10))
        assertEquals(DocumentFileError.UNSUPPORTED_TYPE, checkPickedFile("video/mp4", 10))
        assertEquals(DocumentFileError.UNSUPPORTED_TYPE, checkPickedFile("image/", 10))
        assertEquals(DocumentFileError.UNSUPPORTED_TYPE, checkPickedFile(null, 10))
        // Unsupported wins over too large: the type is the first thing to fix.
        assertEquals(DocumentFileError.UNSUPPORTED_TYPE, checkPickedFile("text/plain", MAX_DOCUMENT_BYTES + 1))
    }

    @Test
    fun sizeLimit_is20Megabytes() {
        assertEquals(20L * 1024 * 1024, MAX_DOCUMENT_BYTES)
        assertNull(checkPickedFile("application/pdf", MAX_DOCUMENT_BYTES))
        assertEquals(DocumentFileError.TOO_LARGE, checkPickedFile("application/pdf", MAX_DOCUMENT_BYTES + 1))
        assertEquals(DocumentFileError.TOO_LARGE, checkPickedFile("image/jpeg", 50L * 1024 * 1024))
    }

    @Test
    fun unknownSize_passesTheCheck() {
        // The copy stops at the limit instead.
        assertNull(checkPickedFile("image/png", null))
    }

    @Test
    fun badge_andExtension() {
        assertEquals(DocumentKind.PDF, documentKind("application/pdf"))
        assertEquals(DocumentKind.PHOTO, documentKind("image/webp"))
        assertEquals(DocumentKind.PHOTO, documentKind("image/jpeg; charset=binary"))
        assertNull(documentKind("text/html"))
        assertEquals("pdf", extensionFor("application/pdf"))
        assertEquals("jpg", extensionFor("image/jpeg"))
        assertEquals("png", extensionFor("image/png"))
        assertEquals("bin", extensionFor("image/x-unknown"))
    }

    @Test
    fun defaultName_dropsTheExtension() {
        assertEquals("Flight tickets", defaultDocumentName("Flight tickets.pdf"))
        assertEquals("archive.tar", defaultDocumentName("archive.tar.gz"))
        assertEquals(".hidden", defaultDocumentName(".hidden"))
        assertEquals("ביטוח נסיעות", defaultDocumentName("ביטוח נסיעות.jpg"))
        assertEquals("", defaultDocumentName(null))
        assertEquals("", defaultDocumentName("  "))
    }

    @Test
    fun input_isTrimmed() {
        val clean = DocumentInput("  Hotel  ", DocumentType.LODGING, "   ").normalized()
        assertEquals("Hotel", clean.name)
        assertNull(clean.detail)
    }
}
