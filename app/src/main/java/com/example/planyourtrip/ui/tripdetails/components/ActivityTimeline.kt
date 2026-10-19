package com.example.planyourtrip.ui.tripdetails.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.iconRes
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.theme.TintedColor
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing

/** Width of the timeline rail (dot + vertical line) left of the activity cards. */
private val RailWidth = 32.dp

/**
 * One timeline entry: a ring dot in [dotColor] (the activity type's color) and a vertical line on the left that
 * joins the entries above and below, then [content]. [isFirst] / [isLast] stop the line at the first and last
 * dots. [dotTop] is the dot's center from the top of the entry, so it lines up with the card's first text line.
 * [dotBackground] fills the gap around the dot; pass the card color when the timeline sits inside a card.
 */
@Composable
fun TimelineRow(
    isFirst: Boolean,
    isLast: Boolean,
    dotColor: Color,
    modifier: Modifier = Modifier,
    dotBackground: Color = MaterialTheme.colorScheme.background,
    dotTop: Dp = DefaultDotTop,
    bottomGap: Dp = Spacing.l,
    content: @Composable () -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Row(modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(RailWidth)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val dotY = dotTop.toPx()
                    val stroke = RailStroke.toPx()
                    if (!isFirst) drawLine(lineColor, Offset(x, 0f), Offset(x, dotY), stroke)
                    if (!isLast) drawLine(lineColor, Offset(x, dotY), Offset(x, size.height), stroke)
                    // Ring dot: filled background, then a ring and a center in the type color.
                    drawCircle(dotBackground, radius = DotHaloRadius.toPx(), center = Offset(x, dotY))
                    drawCircle(
                        dotColor,
                        radius = DotRingRadius.toPx(),
                        center = Offset(x, dotY),
                        style = Stroke(RailStroke.toPx()),
                    )
                    drawCircle(dotColor, radius = DotCenterRadius.toPx(), center = Offset(x, dotY))
                },
        )
        Spacer(Modifier.width(Spacing.m))
        Box(
            Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else bottomGap)
        ) { content() }
    }
}

/**
 * Plan tab card: time, title, round type icon, note box, type label and "Booked", then the location with an
 * "Open in Maps" button ([onOpenInMaps], shown only when the activity has a location). When [showDate] is set
 * (activities outside the trip dates), the date is shown before the time.
 */
@Composable
fun ActivityCard(
    activity: TripActivity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    /** Opens the activity's location in Google Maps; null = no button. */
    onOpenInMaps: (() -> Unit)? = null,
) {
    val typeColor = ExtendedTheme.colors.activityType(activity.type)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = Dimens.CardElevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    TimeAndTitle(activity, showDate)
                }
                Spacer(Modifier.width(Spacing.m))
                ActivityTypeIcon(activity, typeColor)
            }
            activity.note?.let { note ->
                Spacer(Modifier.height(Spacing.m))
                NoteBox(note, activity)
            }
            Spacer(Modifier.height(Spacing.m))
            ActivityTags(activity, typeColor)
            activity.location?.takeIf { it.isNotBlank() }?.let { location ->
                LocationRow(location, onOpenInMaps)
            }
        }
    }
}

/** "Thu 12 Nov · 09:30" (the date only outside the trip dates; nothing without a time), then the title. */
@Composable
private fun TimeAndTitle(activity: TripActivity, showDate: Boolean) {
    val timeLine = listOfNotNull(
        TripFormatting.date(activity.date).takeIf { showDate },
        activity.startTime?.let { TripFormatting.time(it) },
    ).joinToString(" · ")
    if (timeLine.isNotEmpty()) {
        Text(
            text = timeLine,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(Spacing.xs))
    }
    Text(
        text = activity.title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The type pill and, when booked, a neutral "✓ Booked" pill. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActivityTags(activity: TripActivity, typeColor: TintedColor) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TagPill(stringResource(activity.type.labelRes()), typeColor.container, typeColor.content)
        if (activity.booked) {
            // Neutral with a check, so it never reads as a second type label.
            TagPill(
                stringResource(R.string.itinerary_booked),
                MaterialTheme.colorScheme.surfaceContainerHighest,
                MaterialTheme.colorScheme.onSurface,
                leadingIcon = R.drawable.ic_check,
            )
        }
    }
}

/** The location, with "Open in Maps" ([onOpenInMaps]) lined up under the round type icon. */
@Composable
private fun LocationRow(location: String, onOpenInMaps: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = location,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(top = Spacing.s, bottom = if (onOpenInMaps == null) 0.dp else Spacing.s),
        )
        if (onOpenInMaps != null) {
            // Shifted so the icon is centered under the round type icon above (48dp touch target).
            IconButton(
                onClick = onOpenInMaps,
                modifier = Modifier
                    .padding(start = Spacing.xs)
                    .offset(x = (Dimens.TouchTarget - Dimens.CategoryIconSize) / 2),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_map),
                    contentDescription = stringResource(R.string.itinerary_open_in_maps),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Compact card of the Overview day section: time, type pill, title and the note on one line with an icon. */
@Composable
fun CompactActivityCard(activity: TripActivity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val typeColor = ExtendedTheme.colors.activityType(activity.type)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.mPlus)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = activity.startTime?.let { TripFormatting.time(it) }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TagPill(stringResource(activity.type.labelRes()), typeColor.container, typeColor.content)
            }
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = activity.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            activity.note?.let { note ->
                Spacer(Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        painter = painterResource(activity.type.iconRes()),
                        contentDescription = null,
                        tint = typeColor.content,
                        modifier = Modifier
                            .padding(top = Spacing.xxs)
                            .size(IconSize.xs),
                    )
                    Spacer(Modifier.width(Spacing.xsPlus))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityTypeIcon(activity: TripActivity, color: TintedColor) {
    Box(
        modifier = Modifier
            .size(Dimens.CategoryIconSize)
            .background(color.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(activity.type.iconRes()),
            contentDescription = stringResource(activity.type.labelRes()),
            tint = color.content,
            modifier = Modifier.size(IconSize.m),
        )
    }
}

/** Light gray box with a small notes icon. */
@Composable
private fun NoteBox(note: String, activity: TripActivity) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.small)
            .padding(horizontal = Spacing.m, vertical = Spacing.sPlus),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_notes),
            contentDescription = null,
            tint = ExtendedTheme.colors.activityType(activity.type).content,
            modifier = Modifier
                .padding(top = Spacing.xxs)
                .size(IconSize.xs),
        )
        Spacer(Modifier.width(Spacing.sPlus))
        Text(text = note, style = MaterialTheme.typography.bodyMedium, color = muted)
    }
}

/** Small rounded label ("Activity", "Booked"), with an optional small icon before the text. */
@Composable
fun TagPill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    @DrawableRes leadingIcon: Int? = null,
) {
    Row(
        modifier = modifier
            .background(container, CircleShape)
            .padding(horizontal = Spacing.sPlus, vertical = TagPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(IconSize.xxs),
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

/** The dot before the Overview day title. */
@Composable
fun TitleDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Dimens.DotLarge)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
    )
}

/** Where the rail's dot sits, from the top of an activity card. */
private val DefaultDotTop = 30.dp

/** The rail line and the dot's ring. */
private val RailStroke = 2.dp
private val DotHaloRadius = 11.dp
private val DotRingRadius = 10.dp
private val DotCenterRadius = 5.dp

/** The small "Booked" tag. */
private val TagPaddingVertical = 3.dp
