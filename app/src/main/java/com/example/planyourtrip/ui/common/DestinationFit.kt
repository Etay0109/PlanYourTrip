package com.example.planyourtrip.ui.common

/** How a destination title is drawn: its size (a share of its text style) and whether it stays on one line. */
data class TitleFit(val scale: Float, val singleLine: Boolean)

/**
 * Fits a destination title ("London", "Rio de Janeiro") so it never breaks inside a word, in this order:
 * 1. the whole name on one line, shrinking from full size down to [minScale] if needed;
 * 2. otherwise the largest size at which every word fits the width, wrapping only between words;
 * 3. otherwise (a single word too long even at [minScale]) one line at [minScale], ending with "…".
 *
 * [widthOf] is the one-line width of a text at a scale; [roomAt] is the width the text may take at that scale (the
 * flag before it shrinks with it). Pure, so the rule is unit-tested; DestinationTitle measures with Compose.
 */
fun fitDestinationTitle(
    text: String,
    minScale: Float,
    widthOf: (String, Float) -> Int,
    roomAt: (Float) -> Int,
    step: Float = 0.02f,
): TitleFit {
    val scales = titleScales(minScale, step)
    scales.firstOrNull { widthOf(text, it) <= roomAt(it) }?.let { return TitleFit(it, singleLine = true) }
    val words = text.trim().split(Whitespace).filter { it.isNotEmpty() }
    if (words.size > 1) {
        scales.firstOrNull { scale -> words.all { widthOf(it, scale) <= roomAt(scale) } }
            ?.let { return TitleFit(it, singleLine = false) }
    }
    return TitleFit(minScale, singleLine = true)
}

/** 1, 1 − step, … down to exactly [minScale]. */
internal fun titleScales(minScale: Float, step: Float): List<Float> {
    val min = minScale.coerceIn(0f, 1f)
    val count = kotlin.math.ceil((1f - min) / step - 1e-4f).toInt()
    return (0..count).map { maxOf(1f - it * step, min) }.distinct()
}

private val Whitespace = Regex("\\s+")
