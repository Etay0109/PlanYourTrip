package com.example.planyourtrip.ui.common.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PdfFileNameTest {

    private fun nov(day: Int) = LocalDate.of(2026, 11, day)

    @Test
    fun `destination and same-month dates`() {
        assertEquals("Rome-Italy-12-17-Nov-2026.pdf", pdfFileName("Rome, Italy", nov(12), nov(17)))
    }

    @Test
    fun `single day, two months and two years`() {
        assertEquals("Rome-12-Nov-2026.pdf", pdfFileName("Rome", nov(12), nov(12)))
        assertEquals("Rome-28-Nov-3-Dec-2026.pdf", pdfFileName("Rome", nov(28), LocalDate.of(2026, 12, 3)))
        assertEquals(
            "Rome-28-Dec-2026-3-Jan-2027.pdf",
            pdfFileName("Rome", LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)),
        )
    }

    @Test
    fun `special characters become single dashes and accents stay`() {
        assertEquals("São-Paulo-Rio-12-17-Nov-2026.pdf", pdfFileName("  São Paulo / Rio!! ", nov(12), nov(17)))
        assertEquals("a-b-c-12-17-Nov-2026.pdf", pdfFileName("a\\b:c*?\"<>|", nov(12), nov(17)))
        assertEquals("Paris-12-17-Nov-2026.pdf", pdfFileName("Paris 🇫🇷✈️", nov(12), nov(17)))
    }

    @Test
    fun `Hebrew destination keeps its letters`() {
        assertEquals("תל-אביב-ישראל-12-17-Nov-2026.pdf", pdfFileName("תל אביב, ישראל", nov(12), nov(17)))
    }

    @Test
    fun `nothing usable left falls back to Trip`() {
        assertEquals("Trip-12-17-Nov-2026.pdf", pdfFileName("✈️ ★ / ?", nov(12), nov(17)))
    }

    @Test
    fun `very long destinations are shortened`() {
        val name = pdfFileName("A".repeat(200), nov(12), nov(17))
        assertTrue(name.length < 100)
        assertTrue(name.endsWith("-12-17-Nov-2026.pdf"))
    }
}
