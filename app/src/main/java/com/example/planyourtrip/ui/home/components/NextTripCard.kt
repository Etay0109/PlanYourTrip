package com.example.planyourtrip.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.TripStatus
import com.example.planyourtrip.ui.common.components.DestinationTitle
import com.example.planyourtrip.ui.common.components.HeroPhoto
import com.example.planyourtrip.ui.common.components.HeroPhotoState
import com.example.planyourtrip.ui.common.components.HeroWaves
import com.example.planyourtrip.ui.common.components.chipColor
import com.example.planyourtrip.ui.common.components.destinationMinScale
import com.example.planyourtrip.ui.common.components.destinationOneLineWidth
import com.example.planyourtrip.ui.common.components.drawHeroPhotoScrim
import com.example.planyourtrip.ui.common.components.mutedTextColor
import com.example.planyourtrip.ui.common.components.rememberHeroPhotoState
import com.example.planyourtrip.ui.common.components.TripDatesRow
import com.example.planyourtrip.ui.common.components.TripMoreButton
import com.example.planyourtrip.ui.home.ActiveTrip
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroLabelStyle
import com.example.planyourtrip.ui.theme.HeroPhotoScrim
import com.example.planyourtrip.ui.theme.Spacing

/**
 * Large teal gradient card for the nearest (or ongoing) trip. With a destination photo ([photoUrl], about 960 px)
 * the photo fades in under a scrim that is strong behind the text column and light elsewhere; the countdown box and
 * the menu get a darker chip, so everything stays readable on any photo.
 */
@Composable
fun NextTripCard(
    next: ActiveTrip,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** False for a shared trip someone else owns: the menu offers "Leave trip". */
    isOwner: Boolean = true,
    /** The destination photo; null = the gradient only. */
    photoUrl: String? = null,
    /** Today's year: dates in this year leave it out ("19–24 Oct"); null = always shown. */
    currentYear: Int? = null,
) {
    val colors = ExtendedTheme.colors
    val shape = MaterialTheme.shapes.extraLarge
    val photo = rememberHeroPhotoState(photoUrl)
    val mutedText = photo.mutedTextColor()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.heroGradient)
            .then(photo.heroModifier)
            .clickable(onClick = onClick),
    ) {
        HeroPhoto(photo, Modifier.matchParentSize()) { drawCardScrim(photo, colors.heroPhotoTint, colors.heroPhotoShade) }
        // The decorations belong to the gradient: they fade out as the photo fades in.
        HeroWaves(Modifier.matchParentSize().graphicsLayer { alpha = 1f - photo.visibility })
        BoxWithConstraints {
            // The destination first shrinks a little; if it still can't fit on one line, the countdown box gets
            // narrower before the name wraps between words (DestinationTitle never breaks inside a word).
            val compactCountdown = rememberCompactCountdown(next, constraints.maxWidth)
            Row(
                modifier = Modifier
                    .padding(start = Spacing.xxl, end = Dimens.CardMenuEndPadding, top = Spacing.xxl, bottom = Spacing.xxl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = photo.track(LABEL_AREA)) {
                        Box(
                            Modifier
                                .size(Dimens.Dot)
                                .background(colors.heroLiveDot, CircleShape)
                        )
                        Spacer(Modifier.width(Spacing.s))
                        Text(
                            text = stringResource(R.string.home_next_trip),
                            style = HeroLabelStyle,
                            color = mutedText,
                        )
                    }
                    Spacer(Modifier.height(Spacing.l))
                    DestinationTitle(
                        destination = next.trip.destination,
                        countryCode = next.trip.countryCode,
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onHero,
                        maxLines = 3,
                        modifier = photo.track(TITLE_AREA),
                    )
                    Spacer(Modifier.height(Spacing.l))
                    TripDatesRow(
                        start = next.trip.startDate,
                        end = next.trip.endDate,
                        currentYear = currentYear,
                        color = mutedText,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = photo.track(DATES_AREA),
                    )
                }
                Spacer(Modifier.width(Spacing.m))
                CountdownBox(next.status, photo, compactCountdown)
                // The button's own inset gives the gap to the countdown box. On a photo it sits on a round chip.
                val menuChip = photo.color(Color.Transparent, colors.heroPhotoChip)
                TripMoreButton(
                    next.trip.destination, onShare, onDelete, tint = mutedText, isOwner = isOwner,
                    modifier = Modifier.drawBehind { drawCircle(menuChip, radius = MenuChipRadius.toPx()) },
                )
            }
        }
    }
}

/**
 * True when the destination can't fit on one line even at its smallest size ([destinationMinScale]) beside the
 * regular countdown box, so the box should get narrower. [cardWidth] is the card's width in pixels.
 */
@Composable
private fun rememberCompactCountdown(next: ActiveTrip, cardWidth: Int): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleStyle = MaterialTheme.typography.headlineMedium
    val minScale = destinationMinScale()
    val box = countdownTexts(next.status, compact = false)
    return remember(next.trip.destination, next.trip.countryCode, box, cardWidth, titleStyle, minScale, density) {
        fun width(text: String, style: TextStyle) =
            measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        with(density) {
            val content = box.maxOf { (text, style) -> width(text, style) }
            val boxWidth = maxOf(CountdownMinWidth.roundToPx(), content + 2 * CountdownPadding.roundToPx())
            val fixed = Spacing.xxl.roundToPx() + Dimens.CardMenuEndPadding.roundToPx() + Spacing.m.roundToPx() +
                Dimens.TouchTarget.roundToPx()
            val room = cardWidth - fixed - boxWidth
            destinationOneLineWidth(
                measurer, density, next.trip.destination, next.trip.countryCode, titleStyle, minScale,
            ) > room
        }
    }
}

/** The countdown box's texts and their styles: "Now", or the number and "DAYS"; smaller in the compact box. */
@Composable
private fun countdownTexts(status: TripStatus, compact: Boolean): List<Pair<String, TextStyle>> = when (status) {
    TripStatus.Now -> listOf(stringResource(R.string.home_now) to countdownNowStyle(compact))
    is TripStatus.InDays -> listOf(
        status.days.toString() to countdownNumberStyle(compact),
        pluralStringResource(R.plurals.countdown_unit, status.days) to HeroLabelStyle,
    )
}

@Composable
private fun countdownNowStyle(compact: Boolean): TextStyle =
    if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium

@Composable
private fun countdownNumberStyle(compact: Boolean): TextStyle =
    if (compact) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displaySmall

/** The regular countdown box: at least this wide, with this side padding; the compact one has neither minimum. */
private val CountdownMinWidth = 92.dp
private val CountdownPadding = Spacing.mPlus
private val CompactCountdownPadding = Spacing.s

/** The texts on the left (each wraps its content, so its bounds end where its text ends). */
private const val LABEL_AREA = "label"
private const val TITLE_AREA = "title"
private const val DATES_AREA = "dates"

/** The menu button's chip on a photo (the touch target stays 48dp). */
private val MenuChipRadius = 18.dp

/**
 * The tint, then the shade at [HeroPhotoScrim.TEXT] up to where the label, destination and dates end, easing
 * gradually to [HeroPhotoScrim.OPEN] at the card's right edge (the countdown box and the menu have their own chips),
 * so the photo shows more and more to the right with no visible edge. Until the texts have been measured, the whole
 * card gets the text level.
 */
private fun DrawScope.drawCardScrim(
    photo: HeroPhotoState,
    tint: Color,
    shade: Color,
) {
    val texts = listOf(LABEL_AREA, TITLE_AREA, DATES_AREA).map { photo.boundsOf(it) }
    val stops = if (texts.any { it == null }) {
        listOf(0f to HeroPhotoScrim.TEXT)
    } else {
        val strongUntil = texts.maxOf { it!!.right } + HeroPhotoScrim.TextMargin.toPx()
        val fadeEnd = maxOf(size.width, strongUntil + HeroPhotoScrim.MinFade.toPx())
        listOf(0f to HeroPhotoScrim.TEXT) +
            HeroPhotoScrim.easedStops(strongUntil, fadeEnd, HeroPhotoScrim.TEXT, HeroPhotoScrim.OPEN)
    }
    drawHeroPhotoScrim(tint, shade, stops, vertical = false)
}

@Composable
private fun CountdownBox(status: TripStatus, photo: HeroPhotoState, compact: Boolean) {
    val colors = ExtendedTheme.colors
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = Modifier
            .widthIn(min = if (compact) 0.dp else CountdownMinWidth)
            .clip(shape)
            .background(photo.chipColor())
            .border(Dimens.Hairline, colors.heroOverlayBorder, shape)
            .padding(horizontal = if (compact) CompactCountdownPadding else CountdownPadding, vertical = Spacing.mPlus),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (status) {
            TripStatus.Now -> Text(
                text = stringResource(R.string.home_now),
                style = countdownNowStyle(compact),
                color = colors.onHero,
                maxLines = 1,
                modifier = Modifier.padding(vertical = Spacing.sPlus),
            )
            is TripStatus.InDays -> {
                Text(
                    text = status.days.toString(),
                    style = countdownNumberStyle(compact),
                    color = colors.onHero,
                    maxLines = 1,
                )
                Text(
                    text = pluralStringResource(R.plurals.countdown_unit, status.days),
                    style = HeroLabelStyle,
                    color = photo.mutedTextColor(),
                    maxLines = 1,
                )
            }
        }
    }
}
