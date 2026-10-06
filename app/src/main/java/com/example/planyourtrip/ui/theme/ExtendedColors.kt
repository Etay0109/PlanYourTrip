package com.example.planyourtrip.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.TripColor

/** Background + letter color for a trip's letter square. */
@Immutable
data class PastelColor(val container: Color, val content: Color)

/**
 * Colors of one activity type or expense category: [content] for the icon, label, chart slice and timeline dot,
 * on the light [container] tint.
 */
@Immutable
data class TintedColor(val container: Color, val content: Color)

/** Brand colors that have no Material 3 color role. */
@Immutable
data class ExtendedColors(
    val heroGradientStart: Color,
    val heroGradientEnd: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val heroLiveDot: Color,
    /** Translucent fill and border of the countdown box on the hero card. */
    val heroOverlay: Color,
    val heroOverlayBorder: Color,
    /** Decorative wave lines and glow drawn on the hero card. */
    val heroWave: Color,
    val heroGlow: Color,
    val badgeContainer: Color,
    val onBadge: Color,
    val pastCardContainer: Color,
    val pastCardBorder: Color,
    val pastLetterContainer: Color,
    val onPastCard: Color,
    val onPastCardMuted: Color,
    val chevron: Color,
    val pastels: Map<TripColor, PastelColor>,
    /** Budget progress bar once more than 80% is spent, and the "over budget" text. */
    val budgetWarning: Color,
    /** Empty part of the budget progress bar. */
    val budgetTrack: Color,
    /** Background of the round icon chips on trip cards (wallet, bookmark, plan). */
    val iconChip: Color,
    /** Expense categories: one clearly different hue each, shared with activity types where the meaning matches. */
    val categories: Map<ExpenseCategory, TintedColor>,
    /** Itinerary types: one clearly different hue each (teal, rose, indigo, purple, gray; never orange). */
    val activityTypes: Map<ActivityType, TintedColor>,
) {
    val heroGradient: Brush
        get() = Brush.linearGradient(listOf(heroGradientStart, heroGradientEnd))

    fun pastel(color: TripColor): PastelColor = pastels.getValue(color)

    fun category(category: ExpenseCategory): TintedColor = categories.getValue(category)

    fun activityType(type: ActivityType): TintedColor = activityTypes.getValue(type)
}

val LightExtendedColors = ExtendedColors(
    heroGradientStart = Teal60,
    heroGradientEnd = Teal30,
    onHero = Color.White,
    onHeroMuted = Color.White.copy(alpha = 0.82f),
    heroLiveDot = Mint,
    heroOverlay = Color.White.copy(alpha = 0.12f),
    heroOverlayBorder = Color.White.copy(alpha = 0.22f),
    heroWave = Color.White.copy(alpha = 0.10f),
    heroGlow = Color.White.copy(alpha = 0.04f),
    badgeContainer = Gray150,
    onBadge = Teal40,
    pastCardContainer = Gray100,
    pastCardBorder = Gray200,
    pastLetterContainer = Gray200,
    onPastCard = Gray600,
    onPastCardMuted = Gray500,
    chevron = Gray400,
    pastels = mapOf(
        TripColor.SKY to PastelColor(Color(0xFFE1F1FD), Color(0xFF1565A8)),
        TripColor.PEACH to PastelColor(Color(0xFFFFEDD8), Color(0xFFC2410C)),
        TripColor.MINT to PastelColor(Color(0xFFDDF5EA), Color(0xFF1B7A55)),
        TripColor.LAVENDER to PastelColor(Color(0xFFECE6FB), Color(0xFF5B3FB0)),
        TripColor.ROSE to PastelColor(Color(0xFFFDE4EA), Color(0xFFB4235A)),
        TripColor.LEMON to PastelColor(Color(0xFFFDF4CF), Color(0xFF8A6200)),
    ),
    budgetWarning = BudgetOrange,
    budgetTrack = Gray150,
    iconChip = Gray100,
    categories = mapOf(
        ExpenseCategory.FLIGHTS to TintedColor(HueSkyContainer, HueSky),
        ExpenseCategory.HOTELS to TintedColor(HuePurpleContainer, HuePurple),
        ExpenseCategory.FOOD to TintedColor(HueRoseContainer, HueRose),
        ExpenseCategory.SHOPPING to TintedColor(HueGreenContainer, HueGreen),
        ExpenseCategory.TRANSPORT to TintedColor(HueIndigoContainer, HueIndigo),
        ExpenseCategory.ATTRACTIONS to TintedColor(HueGreenTealContainer, HueGreenTeal),
        ExpenseCategory.OTHER to TintedColor(HueGrayContainer, HueGray),
    ),
    activityTypes = mapOf(
        ActivityType.ACTIVITY to TintedColor(HueTealContainer, HueTeal),
        ActivityType.DINING to TintedColor(HueRoseContainer, HueRose),
        ActivityType.TRANSPORT to TintedColor(HueIndigoContainer, HueIndigo),
        ActivityType.LODGING to TintedColor(HuePurpleContainer, HuePurple),
        ActivityType.OTHER to TintedColor(HueGrayContainer, HueGray),
    ),
)

val DarkExtendedColors = ExtendedColors(
    heroGradientStart = Teal50,
    heroGradientEnd = Teal10,
    onHero = Color.White,
    onHeroMuted = Color.White.copy(alpha = 0.8f),
    heroLiveDot = Mint,
    heroOverlay = Color.White.copy(alpha = 0.12f),
    heroOverlayBorder = Color.White.copy(alpha = 0.22f),
    heroWave = Color.White.copy(alpha = 0.10f),
    heroGlow = Color.White.copy(alpha = 0.04f),
    badgeContainer = NightSurfaceHigh,
    onBadge = Teal80,
    pastCardContainer = Color(0xFF151B1E),
    pastCardBorder = NightOutline,
    pastLetterContainer = NightSurfaceHigh,
    onPastCard = NightTextMuted,
    onPastCardMuted = Color(0xFF7D878E),
    chevron = Color(0xFF6E7880),
    pastels = mapOf(
        TripColor.SKY to PastelColor(Color(0xFF16344A), Color(0xFFA6D4F7)),
        TripColor.PEACH to PastelColor(Color(0xFF452A16), Color(0xFFFFC08F)),
        TripColor.MINT to PastelColor(Color(0xFF173B2D), Color(0xFF9BE3C3)),
        TripColor.LAVENDER to PastelColor(Color(0xFF2E2650), Color(0xFFCFC2FA)),
        TripColor.ROSE to PastelColor(Color(0xFF47202D), Color(0xFFF8B4C8)),
        TripColor.LEMON to PastelColor(Color(0xFF3D3414), Color(0xFFF1D77A)),
    ),
    budgetWarning = BudgetOrangeDark,
    budgetTrack = NightSurfaceHigh,
    iconChip = NightSurfaceHigh,
    categories = mapOf(
        ExpenseCategory.FLIGHTS to TintedColor(HueSkyContainerDark, HueSkyDark),
        ExpenseCategory.HOTELS to TintedColor(HuePurpleContainerDark, HuePurpleDark),
        ExpenseCategory.FOOD to TintedColor(HueRoseContainerDark, HueRoseDark),
        ExpenseCategory.SHOPPING to TintedColor(HueGreenContainerDark, HueGreenDark),
        ExpenseCategory.TRANSPORT to TintedColor(HueIndigoContainerDark, HueIndigoDark),
        ExpenseCategory.ATTRACTIONS to TintedColor(HueGreenTealContainerDark, HueGreenTealDark),
        ExpenseCategory.OTHER to TintedColor(HueGrayContainerDark, HueGrayDark),
    ),
    activityTypes = mapOf(
        ActivityType.ACTIVITY to TintedColor(HueTealContainerDark, HueTealDark),
        ActivityType.DINING to TintedColor(HueRoseContainerDark, HueRoseDark),
        ActivityType.TRANSPORT to TintedColor(HueIndigoContainerDark, HueIndigoDark),
        ActivityType.LODGING to TintedColor(HuePurpleContainerDark, HuePurpleDark),
        ActivityType.OTHER to TintedColor(HueGrayContainerDark, HueGrayDark),
    ),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Access point for brand colors outside the Material color scheme: `ExtendedTheme.colors.heroGradient`. */
object ExtendedTheme {
    val colors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}
