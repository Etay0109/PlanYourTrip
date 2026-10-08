package com.example.planyourtrip.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.planyourtrip.R

/** Inter (SIL OFL 1.1, see third_party/inter/OFL.txt). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private val base = Typography()

private fun TextStyle.inter(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Float = 0f,
) = copy(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)

val Typography = Typography(
    // Countdown number on the hero card
    displaySmall = base.displaySmall.inter(FontWeight.Bold, 44, 50, -0.5f),
    // "My Trips"
    headlineLarge = base.headlineLarge.inter(FontWeight.Bold, 40, 46, -0.8f),
    // Hero destination, trip details title
    headlineMedium = base.headlineMedium.inter(FontWeight.Bold, 30, 36, -0.4f),
    // Section titles ("Upcoming", "Past trips")
    headlineSmall = base.headlineSmall.inter(FontWeight.SemiBold, 24, 30, -0.2f),
    // Card titles
    titleLarge = base.titleLarge.inter(FontWeight.SemiBold, 20, 26, -0.1f),
    titleMedium = base.titleMedium.inter(FontWeight.SemiBold, 16, 22),
    titleSmall = base.titleSmall.inter(FontWeight.Medium, 14, 20),
    bodyLarge = base.bodyLarge.inter(FontWeight.Normal, 17, 24),
    bodyMedium = base.bodyMedium.inter(FontWeight.Normal, 15, 21),
    bodySmall = base.bodySmall.inter(FontWeight.Normal, 13, 18),
    // Buttons, "NEXT TRIP" label
    labelLarge = base.labelLarge.inter(FontWeight.SemiBold, 15, 20, 0.1f),
    // Badges
    labelMedium = base.labelMedium.inter(FontWeight.SemiBold, 14, 18),
    labelSmall = base.labelSmall.inter(FontWeight.Medium, 12, 16, 0.4f),
    displayLarge = base.displayLarge.copy(fontFamily = Inter),
    displayMedium = base.displayMedium.copy(fontFamily = Inter),
)

/** Letter-spaced caps label: hero card ("NEXT TRIP", "DAYS") and the budget card ("TRIP BUDGET"). */
val HeroLabelStyle = Typography.labelLarge.copy(letterSpacing = 1.5.sp)

/**
 * Headline for the Material 3 DatePicker, which uses headlineLarge internally.
 * The app's headlineLarge (40sp bold, "My Trips") is too loud there.
 */
val DatePickerHeadlineStyle = Typography.headlineMedium.copy(
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
)

/**
 * Converter amounts start at headlineSmall (24sp) and step down to 10sp until the number fits its box, so long
 * amounts stay fully visible. Each step keeps headlineSmall's line height, so the field never changes height.
 */
val ConverterAmountStyles: List<TextStyle> =
    (24 downTo 10).map { size -> Typography.headlineSmall.copy(fontSize = size.sp) }

/**
 * Initials inside a round traveler avatar of [avatarSize]: about 40% of the circle for one letter, 28% for "YOU".
 * Divided by [fontScale] so the letters always fit the circle; avatars are decorative (the name is always written
 * next to them and read by TalkBack), so they don't need to grow with the font size.
 */
fun travelerAvatarTextStyle(avatarSize: Dp, letters: Int, fontScale: Float): TextStyle {
    val factor = if (letters <= 1) 0.4f else 0.28f
    return Typography.labelLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = (avatarSize.value * factor / fontScale).sp,
        lineHeight = (avatarSize.value * factor / fontScale).sp,
        letterSpacing = 0.sp,
    )
}
