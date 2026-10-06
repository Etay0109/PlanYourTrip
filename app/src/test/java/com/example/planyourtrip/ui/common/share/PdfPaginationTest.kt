package com.example.planyourtrip.ui.common.share

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPaginationTest {

    private fun block(height: Float, keepWithNext: Boolean = false) = PdfBlockSize(height, keepWithNext)

    @Test
    fun `everything fits on one page`() {
        assertEquals(listOf(listOf(0, 1, 2)), paginate(listOf(block(100f), block(200f), block(300f)), 700f))
    }

    @Test
    fun `no blocks, no pages`() {
        assertEquals(emptyList<List<Int>>(), paginate(emptyList(), 700f))
    }

    @Test
    fun `a block that doesn't fit moves to the next page whole`() {
        assertEquals(
            listOf(listOf(0, 1), listOf(2)),
            paginate(listOf(block(300f), block(300f), block(200f)), 700f),
        )
    }

    @Test
    fun `a block exactly filling the page stays on it`() {
        assertEquals(listOf(listOf(0, 1), listOf(2)), paginate(listOf(block(400f), block(300f), block(10f)), 700f))
    }

    @Test
    fun `a day heading moves to the next page with its first activity`() {
        // Heading (50) would fit at the bottom of page 1, but its first activity (100) wouldn't.
        val blocks = listOf(block(600f), block(50f, keepWithNext = true), block(100f), block(100f))
        assertEquals(listOf(listOf(0), listOf(1, 2, 3)), paginate(blocks, 700f))
    }

    @Test
    fun `section title, day heading and first activity stay together`() {
        val blocks = listOf(block(560f), block(40f, true), block(40f, true), block(100f))
        assertEquals(listOf(listOf(0), listOf(1, 2, 3)), paginate(blocks, 700f))
    }

    @Test
    fun `a block taller than a page gets a page of its own`() {
        val blocks = listOf(block(100f), block(900f), block(100f))
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), paginate(blocks, 700f))
    }

    @Test
    fun `a too-tall group still keeps a heading on the page of its oversized activity`() {
        val blocks = listOf(block(100f), block(50f, true), block(900f), block(100f))
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), paginate(blocks, 700f))
    }

    @Test
    fun `a trailing keep-with-next block is placed normally`() {
        assertEquals(listOf(listOf(0, 1)), paginate(listOf(block(100f), block(50f, true)), 700f))
    }
}
