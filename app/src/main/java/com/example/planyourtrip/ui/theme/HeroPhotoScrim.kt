package com.example.planyourtrip.ui.theme

import androidx.compose.ui.unit.dp

/**
 * How strongly [ExtendedColors.heroPhotoShade] covers a destination photo, by what sits on it (alpha 0..1, drawn over
 * [ExtendedColors.heroPhotoTint]). The scrim is strong only where text is, so the photo stays recognizable:
 * `HeroPhotoTest` checks every level on a pure-white and a pure-black pixel in both themes.
 */
object HeroPhotoScrim {
    /** Small text (dates, "NEXT TRIP", the countdown unit): white text ≥ 4.5:1 on any pixel. */
    const val TEXT = 0.55f

    /** The large bold destination: white ≥ 3:1 (WCAG large text) on any pixel. */
    const val LARGE_TEXT = 0.40f

    /** Where no text sits: the photo shows (the whole scrim ≤ 0.4 opaque). */
    const val OPEN = 0.10f

    /** The top of the trip header, under the status bar's icons (the whole scrim ≤ 0.4 opaque). */
    const val STATUS_BAR = 0.18f

    /**
     * The shortest distance over which the shade changes level (where there is room, the Next trip card fades over
     * everything right of its text). Long and eased ([easedStops]), so the photo never shows an edge.
     */
    val MinFade = 96.dp

    /** Extra cover around a text block, so the first and last lines sit fully on their level. */
    val TextMargin = 8.dp

    /** Stops per eased fade: enough that the gradient between two stops is visually straight. */
    const val FADE_STEPS = 16

    /**
     * Gradient stops (position, alpha) going from [alphaFrom] at [from] to [alphaTo] at [to] along a smoothstep curve:
     * flat at both ends, so the fade blends into the levels around it with no visible edge.
     */
    fun easedStops(
        from: Float,
        to: Float,
        alphaFrom: Float,
        alphaTo: Float,
        steps: Int = FADE_STEPS,
    ): List<Pair<Float, Float>> = (0..steps).map { i ->
        if (i == steps) return@map to to alphaTo // exactly on the next level, whatever the float rounding
        val t = i.toFloat() / steps
        val eased = t * t * (3f - 2f * t)
        (from + (to - from) * t) to (alphaFrom + (alphaTo - alphaFrom) * eased)
    }
}
