package com.example.planyourtrip.ui.theme

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.planyourtrip.data.trip.ActivityType

/**
 * Colors of the shared trip PDF, as ARGB ints for android.graphics. Paper is always light, so only the light
 * palette is used. Raw values stay in Color.kt.
 */
object PdfColors {
    /** The hero gradient of the app, from top-left to bottom-right. */
    @ColorInt val heroStart: Int = LightExtendedColors.heroGradientStart.toArgb()
    @ColorInt val heroEnd: Int = LightExtendedColors.heroGradientEnd.toArgb()
    @ColorInt val onHero: Int = Color.White.toArgb()
    @ColorInt val onHeroMuted: Int = Teal90.toArgb()
    @ColorInt val heading: Int = Teal50.toArgb()
    @ColorInt val text: Int = Ink.toArgb()
    @ColorInt val textMuted: Int = Gray600.toArgb()
    @ColorInt val divider: Int = Gray200.toArgb()
    @ColorInt val sectionBackground: Int = Teal95.toArgb()
    @ColorInt val underBudget: Int = Teal50.toArgb()
    @ColorInt val overBudget: Int = ErrorRed.toArgb()

    /** The type's hue, as in the app (ExtendedColors.activityTypes, light). */
    @ColorInt
    fun activityType(type: ActivityType): Int = LightExtendedColors.activityType(type).content.toArgb()
}
