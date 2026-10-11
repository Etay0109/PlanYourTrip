package com.example.planyourtrip.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.TripStatus
import com.example.planyourtrip.ui.common.components.DestinationTitle
import com.example.planyourtrip.ui.common.components.HeroPhoto
import com.example.planyourtrip.ui.common.components.HeroPhotoState
import com.example.planyourtrip.ui.common.components.HeroWaves
import com.example.planyourtrip.ui.common.components.chipColor
import com.example.planyourtrip.ui.common.components.drawHeroPhotoScrim
import com.example.planyourtrip.ui.common.components.mutedTextColor
import com.example.planyourtrip.ui.common.components.rememberHeroPhotoState
import com.example.planyourtrip.ui.common.components.TripDatesRow
import com.example.planyourtrip.ui.common.components.TripMoreButton
import com.example.planyourtrip.ui.common.datesWithLength
import com.example.planyourtrip.ui.home.ActiveTrip
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroLabelStyle
import com.example.planyourtrip.ui.theme.HeroPhotoScrim

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
        Row(
            modifier = Modifier.padding(start = 24.dp, end = Dimens.CardMenuEndPadding, top = 24.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = photo.track(LABEL_AREA)) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(colors.heroLiveDot, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_next_trip),
                        style = HeroLabelStyle,
                        color = mutedText,
                    )
                }
                Spacer(Modifier.height(16.dp))
                DestinationTitle(
                    destination = next.trip.destination,
                    countryCode = next.trip.countryCode,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onHero,
                    maxLines = 3,
                    modifier = photo.track(TITLE_AREA),
                )
                Spacer(Modifier.height(16.dp))
                TripDatesRow(
                    text = datesWithLength(next.trip),
                    color = mutedText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = photo.track(DATES_AREA),
                )
            }
            Spacer(Modifier.width(12.dp))
            CountdownBox(next.status, photo)
            // The button's own inset gives the gap to the countdown box. On a photo it sits on a round chip.
            val menuChip = photo.color(Color.Transparent, colors.heroPhotoChip)
            TripMoreButton(
                next.trip.destination, onShare, onDelete, tint = mutedText, isOwner = isOwner,
                modifier = Modifier.drawBehind { drawCircle(menuChip, radius = MenuChipRadius.toPx()) },
            )
        }
    }
}

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
private fun CountdownBox(status: TripStatus, photo: HeroPhotoState) {
    val colors = ExtendedTheme.colors
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = Modifier
            .widthIn(min = 92.dp)
            .clip(shape)
            .background(photo.chipColor())
            .border(1.dp, colors.heroOverlayBorder, shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (status) {
            TripStatus.Now -> Text(
                text = stringResource(R.string.home_now),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onHero,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            is TripStatus.InDays -> {
                Text(
                    text = status.days.toString(),
                    style = MaterialTheme.typography.displaySmall,
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
