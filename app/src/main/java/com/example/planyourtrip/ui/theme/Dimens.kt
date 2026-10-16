package com.example.planyourtrip.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Shared size and spacing values from the design. */
object Dimens {
    /** Horizontal padding of full-width screens (home list, new trip, trip details). */
    val ScreenPadding = 20.dp

    /** Profile avatar and trip letter squares. */
    val AvatarSize = 56.dp

    /** Height of the pill-shaped primary buttons. */
    val ButtonHeight = 52.dp

    /** Round icon chips on trip cards (wallet, bookmark, plan) and the header's circle buttons. */
    val IconChipSize = 44.dp

    /** Rounded category icon squares in the expense list and the chart legend. */
    val CategoryIconSize = 40.dp

    /** Minimum touch target of icon buttons (Material). */
    val TouchTarget = 48.dp

    /** Three-dots icon at the right edge of the home trip cards, drawn inside a [TouchTarget] button. */
    val CardMenuIconSize = 20.dp

    /** Distance from the card's right edge to the three-dots icon, the same on every home card. */
    val CardMenuIconInset = 16.dp

    /**
     * End padding of a home card's row so the icon, centered in its 48dp button, sits [CardMenuIconInset] from the
     * edge: 16 − (48 − 20) / 2 = 2dp.
     */
    val CardMenuEndPadding = CardMenuIconInset - (TouchTarget - CardMenuIconSize) / 2

    /** The red "Delete" area a home trip card shows when it is swiped to the left (and stays open). */
    val SwipeRevealWidth = 96.dp

    /** The Overview map card's picture: at least this tall, taller when the Daily plan card next to it is. */
    val MapPreviewMinHeight = 88.dp

    /** The flag (or pin) slot at the start of a destination suggestion row. */
    val SuggestionFlagWidth = 32.dp

    /** The thin bar under the destination field while places are looked up (fits the gap to the next field). */
    val SearchBarHeight = 3.dp

    /** Width of the Prep tab's document cards (and the dashed "Add document" card). */
    val DocumentCardWidth = 200.dp

    /** The round check of a Prep checklist row. */
    val ChecklistCheckSize = 26.dp
    /** The unchecked circle's ring. */
    val ChecklistCheckBorder = 2.dp

    /** Round traveler avatars in the Budget tab's Travelers row (and the dashed "+ Add" circle). */
    val TravelerAvatarSize = 56.dp

    /** Traveler avatars in the "Who owes whom" rows. */
    val TravelerAvatarSmallSize = 32.dp

    /** The payer's avatar on an expense row. */
    val TravelerAvatarTinySize = 18.dp

    /** Avatars inside the expense form's "Paid by" / "Split between" chips (the chip's leading icon size). */
    val TravelerAvatarChipSize = 18.dp

    /** Sign In / Sign Up: share of the screen height the hero photo takes (it grows when its text needs more). */
    const val AuthHeroHeightFraction = 0.42f

    /** Sign In / Sign Up: how far the white sheet's rounded top overlaps the hero, and its corner radius. */
    val AuthSheetCorner = 32.dp

    /** Sign In / Sign Up: how far below the status bar its fixed scrim fades out. */
    val AuthStatusBarFade = 16.dp

    /**
     * Trip screen: how far below the status bar its shade fades out, and how far the list scrolls while the shade
     * fades in.
     */
    val TripStatusBarFade = 16.dp

    /** Sign In / Sign Up: the app icon on the hero. */
    val AuthAppIconSize = 64.dp

    /** Sign In / Sign Up: height of the text fields and the primary button. */
    val AuthFieldHeight = 56.dp

    /** Sign In / Sign Up: the focused or error border of a text field. */
    val AuthFieldBorder = 1.5.dp

    /** Screens (or cards) narrower than this move side-by-side parts under each other (badges, currency selector). */
    val CompactScreenWidth = 360.dp

    /** Content narrower than this stacks the header pill, the converter rate pill and the category chart. */
    val NarrowContentWidth = 300.dp

    /** Font scales above this get the same stacked layouts as narrow screens. */
    const val LargeFontScale = 1.2f
}

/** True when the user's font scale is above [Dimens.LargeFontScale]: layouts then stack like on narrow screens. */
@Composable
fun isLargeFontScale(): Boolean = LocalDensity.current.fontScale > Dimens.LargeFontScale
