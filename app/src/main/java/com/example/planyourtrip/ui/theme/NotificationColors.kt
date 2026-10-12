package com.example.planyourtrip.ui.theme

import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.toArgb

/** Colors of the app's notifications, as ARGB ints. Raw values stay in Color.kt. */
object NotificationColors {
    /** The brand teal (the small icon's tint and the accent of the trip reminder). */
    @ColorInt val brand: Int = Teal50.toArgb()
}
