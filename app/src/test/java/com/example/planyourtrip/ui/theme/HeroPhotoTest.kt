package com.example.planyourtrip.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scrim over a destination photo ([HeroPhotoScrim] + the `heroPhoto…` tokens): text stays readable on any photo
 * (checked on a pure-white and a pure-black pixel, the extremes) while the photo stays visible where no text sits.
 * On a photo the hero's muted texts turn full [ExtendedColors.onHero], so every text is checked in that color.
 */
class HeroPhotoTest {

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private val schemes = mapOf("light" to LightExtendedColors, "dark" to DarkExtendedColors)
    private val pixels = mapOf("white" to Color.White, "black" to Color.Black)

    /** What a pixel of the photo looks like under the tint and the shade at [shadeAlpha]. */
    private fun scrimmed(colors: ExtendedColors, pixel: Color, shadeAlpha: Float): Color =
        colors.heroPhotoShade.copy(alpha = shadeAlpha).compositeOver(colors.heroPhotoTint.compositeOver(pixel))

    private fun forEveryCase(check: (label: String, colors: ExtendedColors, pixel: Color) -> Unit) {
        schemes.forEach { (mode, colors) ->
            pixels.forEach { (pixelName, pixel) -> check("$mode, $pixelName pixel", colors, pixel) }
        }
    }

    @Test
    fun smallText_dates_label_countdownUnit_isAtLeast4_5() {
        forEveryCase { label, colors, pixel ->
            val ratio = contrast(colors.onHero, scrimmed(colors, pixel, HeroPhotoScrim.TEXT))
            assertTrue("small text, $label: $ratio < 4.5", ratio >= 4.5f)
        }
    }

    @Test
    fun largeBoldTitle_isAtLeast3() {
        forEveryCase { label, colors, pixel ->
            val ratio = contrast(colors.onHero, scrimmed(colors, pixel, HeroPhotoScrim.LARGE_TEXT))
            assertTrue("large title, $label: $ratio < 3", ratio >= 3f)
        }
    }

    @Test
    fun chips_countdownBox_statusChip_buttons_areAtLeast4_5_evenWhereThePhotoShows() {
        forEveryCase { label, colors, pixel ->
            // The lightest place a chip can sit: the open area (the countdown box, the header's buttons).
            val under = scrimmed(colors, pixel, HeroPhotoScrim.OPEN)
            val ratio = contrast(colors.onHero, colors.heroPhotoChip.compositeOver(under))
            assertTrue("chip text, $label: $ratio < 4.5", ratio >= 4.5f)
        }
    }

    @Test
    fun thePhotoStaysVisible_whereNoTextSits() {
        schemes.forEach { (mode, colors) ->
            listOf("open area" to HeroPhotoScrim.OPEN, "status bar" to HeroPhotoScrim.STATUS_BAR).forEach { (area, a) ->
                val total = 1f - (1f - colors.heroPhotoTint.alpha) * (1f - a)
                assertTrue("$area in $mode: the scrim is $total opaque (> 0.4)", total <= 0.4f + 1e-4f)
            }
        }
    }

    @Test
    fun levels_growTowardTheText() {
        assertTrue(HeroPhotoScrim.OPEN < HeroPhotoScrim.LARGE_TEXT)
        assertTrue(HeroPhotoScrim.LARGE_TEXT < HeroPhotoScrim.TEXT)
        assertTrue(HeroPhotoScrim.STATUS_BAR < HeroPhotoScrim.LARGE_TEXT)
    }

    @Test
    fun easedFade_startsAndEndsExactlyOnItsLevels_andNeverOvershoots() {
        val stops = HeroPhotoScrim.easedStops(100f, 500f, HeroPhotoScrim.TEXT, HeroPhotoScrim.OPEN)
        assertTrue(stops.first() == 100f to HeroPhotoScrim.TEXT)
        assertTrue(stops.last() == 500f to HeroPhotoScrim.OPEN)
        stops.zipWithNext().forEach { (a, b) ->
            assertTrue("positions go forward", b.first > a.first)
            assertTrue("alpha only falls", b.second <= a.second)
        }
    }

    @Test
    fun easedFade_hasNoVisibleEdge() {
        val stops = HeroPhotoScrim.easedStops(0f, 1f, HeroPhotoScrim.TEXT, HeroPhotoScrim.OPEN)
        val steps = stops.zipWithNext { a, b -> a.second - b.second }
        // No single stop carries more than a tenth of the change (a straight 40dp drop was the seam)…
        steps.forEach { assertTrue("step $it", it <= (HeroPhotoScrim.TEXT - HeroPhotoScrim.OPEN) / 10f) }
        // …and it starts and ends flat, so it blends into the levels on both sides.
        assertTrue(steps.first() < steps[steps.size / 2] / 4f)
        assertTrue(steps.last() < steps[steps.size / 2] / 4f)
    }

    @Test
    fun theFadeIsLong() {
        assertTrue(HeroPhotoScrim.MinFade.value >= 96f)
    }
}
