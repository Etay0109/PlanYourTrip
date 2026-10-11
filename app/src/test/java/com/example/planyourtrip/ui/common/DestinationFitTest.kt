package com.example.planyourtrip.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationFitTest {

    /** Every character is 10 px wide at full size; [room] px are available. */
    private fun fit(text: String, room: Int, minScale: Float = 0.8f) = fitDestinationTitle(
        text,
        minScale,
        widthOf = { t, scale -> (t.length * 10 * scale).toInt() },
        roomAt = { room },
    )

    @Test
    fun fitsAtFullSize_staysOnOneLineAtFullSize() {
        assertEquals(TitleFit(1f, singleLine = true), fit("London", room = 60))
    }

    @Test
    fun slightlyTooWide_shrinksJustEnough_onOneLine() {
        // "London" is 60 px; 54 px of room → 0.9.
        val result = fit("London", room = 54)
        assertTrue(result.singleLine)
        assertEquals(0.9f, result.scale, 0.001f)
    }

    @Test
    fun singleWordTooLongEvenAtTheMinimum_staysOnOneLineAtTheMinimum() {
        // Never broken inside the word: one line at the minimum size, ending with "…".
        assertEquals(TitleFit(0.8f, singleLine = true), fit("Llanfairpwllgwyngyll", room = 100))
    }

    @Test
    fun twoWords_shrinkFirst_onOneLine() {
        // "Rio Grande" is 100 px; 85 px of room → 0.84, still one line.
        val result = fit("Rio Grande", room = 85)
        assertTrue(result.singleLine)
        assertEquals(0.84f, result.scale, 0.001f)
    }

    @Test
    fun twoWords_tooWideEvenAtTheMinimum_wrapBetweenWordsAtTheLargestSizeWhereEachWordFits() {
        // "Buenos Aires" needs 120 px; 0.8 × 120 = 96 > 70. Each word (60 px) fits 70 px at full size.
        assertEquals(TitleFit(1f, singleLine = false), fit("Buenos Aires", room = 70))
        // "Buenos" no longer fits at full size in 54 px: the largest size where both words fit is 0.9 (54 px).
        val smaller = fit("Buenos Aires", room = 54)
        assertEquals(false, smaller.singleLine)
        assertEquals(0.9f, smaller.scale, 0.001f)
    }

    @Test
    fun aWordTooLongEvenAtTheMinimumAmongOthers_staysOnOneLineWithEllipsis() {
        assertEquals(TitleFit(0.8f, singleLine = true), fit("Llanfairpwllgwyngyll town", room = 100))
    }

    @Test
    fun scales_goFromFullSizeDownToExactlyTheMinimum() {
        val scales = titleScales(0.8f, 0.02f)
        assertEquals(1f, scales.first(), 0f)
        assertEquals(0.8f, scales.last(), 0f)
        assertTrue(scales.zipWithNext().all { (a, b) -> a > b })
    }
}
