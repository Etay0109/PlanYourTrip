package com.example.planyourtrip.ui.tripdetails.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Trip
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
import com.example.planyourtrip.ui.common.components.TripMoreMenu
import com.example.planyourtrip.ui.common.datesWithLength
import com.example.planyourtrip.ui.common.statusLabel
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroPhotoScrim
import com.example.planyourtrip.ui.theme.isLargeFontScale

/**
 * Teal gradient header of the trip screen: back, countdown badge, edit, the three-dots menu ("Share trip"),
 * destination and dates.
 * It draws behind the status bar (the app is edge-to-edge) and pads its content below it. With a destination photo
 * ([photoUrl]) the photo fades in under a scrim that is light at the top and strong behind the destination and dates;
 * the buttons and the status chip get a darker fill.
 */
@Composable
fun TripHeader(
    trip: Trip,
    status: TripStatus?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** False for a shared trip someone else owns: the menu offers "Leave trip" instead of "Delete trip". */
    isOwner: Boolean = true,
    /** The destination photo; null = the gradient only. */
    photoUrl: String? = null,
) {
    val colors = ExtendedTheme.colors
    val shape = MaterialTheme.shapes.extraLarge.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))
    val photo = rememberHeroPhotoState(photoUrl)
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.heroGradient)
            .then(photo.heroModifier),
    ) {
        HeroPhoto(photo, Modifier.matchParentSize()) {
            drawHeaderScrim(photo, statusBarHeight, colors.heroPhotoTint, colors.heroPhotoShade)
        }
        // The decorations belong to the gradient: they fade out as the photo fades in.
        HeroWaves(Modifier.matchParentSize().graphicsLayer { alpha = 1f - photo.visibility })
        BoxWithConstraints(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 12.dp, bottom = 28.dp),
        ) {
            // Three buttons leave little room: on narrow screens or large fonts the badge gets its own line.
            val badgeInline = maxWidth >= Dimens.NarrowContentWidth && !isLargeFontScale()
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeaderButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack, photo)
                    Spacer(Modifier.weight(1f))
                    if (status != null && badgeInline) {
                        StatusBadge(statusLabel(status), photo)
                        Spacer(Modifier.width(8.dp))
                    }
                    HeaderButton(R.drawable.ic_edit, stringResource(R.string.trip_edit), onEdit, photo)
                    Spacer(Modifier.width(8.dp))
                    val moreDescription = stringResource(R.string.share_more_options, trip.destination)
                    TripMoreMenu(onShare, onDelete, isOwner = isOwner) { open ->
                        HeaderButton(R.drawable.ic_more_vert, moreDescription, open, photo)
                    }
                }
                if (status != null && !badgeInline) {
                    Spacer(Modifier.height(16.dp))
                    StatusBadge(statusLabel(status), photo)
                }
                Spacer(Modifier.height(20.dp))
                DestinationTitle(
                    destination = trip.destination,
                    countryCode = trip.countryCode,
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.onHero,
                    maxLines = 3,
                    modifier = photo.track(TITLE_AREA),
                )
                Spacer(Modifier.height(8.dp))
                TripDatesRow(
                    text = datesWithLength(trip),
                    color = photo.mutedTextColor(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = photo.track(DATES_AREA),
                )
            }
        }
    }
}

private const val TITLE_AREA = "title"
private const val DATES_AREA = "dates"

/**
 * The tint, then a vertical shade, every change eased ([HeroPhotoScrim.easedStops], no visible edge):
 * [HeroPhotoScrim.STATUS_BAR] under the status bar's icons easing to [HeroPhotoScrim.OPEN] around the buttons (they
 * have their own chips), rising to [HeroPhotoScrim.LARGE_TEXT] where the destination starts and on to
 * [HeroPhotoScrim.TEXT] at the dates line, down to the bottom. Until the text has been measured, the whole header gets
 * the text level.
 */
private fun DrawScope.drawHeaderScrim(photo: HeroPhotoState, statusBarHeight: Float, tint: Color, shade: Color) {
    val title = photo.boundsOf(TITLE_AREA)
    val dates = photo.boundsOf(DATES_AREA)
    val stops = if (title == null || dates == null) {
        listOf(0f to HeroPhotoScrim.TEXT)
    } else {
        val margin = HeroPhotoScrim.TextMargin.toPx()
        val titleStart = title.top - margin
        val datesStart = maxOf(titleStart, dates.top - margin)
        // The status bar's shade fades out over the same distance again below it.
        val openFrom = minOf(statusBarHeight * 2f, titleStart)
        val riseFrom = maxOf(openFrom, titleStart - HeroPhotoScrim.MinFade.toPx())
        HeroPhotoScrim.easedStops(0f, openFrom, HeroPhotoScrim.STATUS_BAR, HeroPhotoScrim.OPEN) +
            HeroPhotoScrim.easedStops(riseFrom, titleStart, HeroPhotoScrim.OPEN, HeroPhotoScrim.LARGE_TEXT) +
            HeroPhotoScrim.easedStops(titleStart, datesStart, HeroPhotoScrim.LARGE_TEXT, HeroPhotoScrim.TEXT) +
            listOf(size.height to HeroPhotoScrim.TEXT)
    }
    drawHeroPhotoScrim(tint, shade, stops, vertical = true)
}

@Composable
private fun HeaderButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, photo: HeroPhotoState) {
    val colors = ExtendedTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = photo.chipColor(),
        contentColor = colors.onHero,
        modifier = Modifier.size(Dimens.IconChipSize + 8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = description, modifier = Modifier.size(24.dp))
        }
    }
}

/** "● In 38 days" pill on the gradient. */
@Composable
private fun StatusBadge(text: String, photo: HeroPhotoState) {
    val colors = ExtendedTheme.colors
    Row(
        modifier = Modifier
            .background(photo.chipColor(), CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(photo.mutedTextColor(), CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = colors.onHero, maxLines = 1)
    }
}
