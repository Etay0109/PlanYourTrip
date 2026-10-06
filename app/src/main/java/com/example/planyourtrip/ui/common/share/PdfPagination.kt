package com.example.planyourtrip.ui.common.share

// Page breaks of the shared PDF (pure, unit-tested in PdfPaginationTest).

/**
 * One block of the PDF body (header, cost section, day heading, activity...) after it was measured.
 * [keepWithNext] keeps it on the same page as the block after it (a day heading and its first activity).
 */
data class PdfBlockSize(val height: Float, val keepWithNext: Boolean = false)

/**
 * Splits [blocks] into pages of at most [pageHeight], returning the block indexes of each page. A block is
 * never split across pages; a [PdfBlockSize.keepWithNext] block moves to the next page together with the one
 * after it. A block (or a heading with its block) taller than a whole page gets a page of its own, where the
 * renderer cuts it at the bottom.
 */
fun paginate(blocks: List<PdfBlockSize>, pageHeight: Float): List<List<Int>> {
    val pages = mutableListOf<MutableList<Int>>()
    var current = mutableListOf<Int>()
    var used = 0f
    var i = 0
    while (i < blocks.size) {
        // A chain of keep-with-next blocks and the block that ends it are placed as one unit.
        var last = i
        while (blocks[last].keepWithNext && last + 1 < blocks.size) last++
        val group = (i..last).toList()
        val groupHeight = group.sumOf { blocks[it].height.toDouble() }.toFloat()
        if (used + groupHeight <= pageHeight) {
            current += group
            used += groupHeight
            i = last + 1
            continue
        }
        if (current.isNotEmpty() && groupHeight <= pageHeight) {
            // Start a new page for the whole group.
            pages += current
            current = mutableListOf()
            used = 0f
            continue
        }
        // The group is taller than a page: it gets a fresh page of its own and is cut at the bottom there.
        if (current.isNotEmpty()) pages += current
        pages += group.toMutableList()
        current = mutableListOf()
        used = 0f
        i = last + 1
    }
    if (current.isNotEmpty()) pages += current
    return pages
}
