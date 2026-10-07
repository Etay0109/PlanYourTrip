package com.example.planyourtrip.ui.theme

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

    /** The Overview map card's picture: at least this tall, taller when the Daily plan card next to it is. */
    val MapPreviewMinHeight = 88.dp

    /** Width of the Prep tab's document cards (and the dashed "Add document" card). */
    val DocumentCardWidth = 200.dp

    /** The round check of a Prep checklist row. */
    val ChecklistCheckSize = 26.dp
}
