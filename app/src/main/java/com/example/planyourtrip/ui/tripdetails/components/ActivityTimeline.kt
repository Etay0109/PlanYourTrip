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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import com.example.planyourtrip.ui.theme.TintedColor
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.tripdetails.iconRes
import com.example.planyourtrip.ui.tripdetails.labelRes

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
    dotTop: Dp = 30.dp,
    bottomGap: Dp = 16.dp,
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
                    val stroke = 2.dp.toPx()
                    if (!isFirst) drawLine(lineColor, Offset(x, 0f), Offset(x, dotY), stroke)
                    if (!isLast) drawLine(lineColor, Offset(x, dotY), Offset(x, size.height), stroke)
                    // Ring dot: filled background, then a ring and a center in the type color.
                    drawCircle(dotBackground, radius = 11.dp.toPx(), center = Offset(x, dotY))
                    drawCircle(
                        dotColor,
                        radius = 10.dp.toPx(),
                        center = Offset(x, dotY),
                        style = Stroke(2.dp.toPx()),
                    )
                    drawCircle(dotColor, radius = 5.dp.toPx(), center = Offset(x, dotY))
                },
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else bottomGap)
        ) { content() }
    }
}

/**
 * Plan tab card: time, title, round type icon, note box, then type label, "Booked" and location. When
 * [showDate] is set (activities outside the trip dates), the date is shown before the time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityCard(
    activity: TripActivity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
) {
    val typeColor = ExtendedTheme.colors.activityType(activity.type)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
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
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        text = activity.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                ActivityTypeIcon(activity, typeColor)
            }
            activity.note?.let { note ->
                Spacer(Modifier.height(12.dp))
                NoteBox(note, activity)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                activity.location?.let { location ->
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
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
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = activity.startTime?.let { TripFormatting.time(it) }.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TagPill(stringResource(activity.type.labelRes()), typeColor.container, typeColor.content)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = activity.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            activity.note?.let { note ->
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        painter = painterResource(activity.type.iconRes()),
                        contentDescription = null,
                        tint = typeColor.content,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
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
            modifier = Modifier.size(20.dp),
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_notes),
            contentDescription = null,
            tint = ExtendedTheme.colors.activityType(activity.type).content,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
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
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

/** The dot before the Overview day title. */
@Composable
fun TitleDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(10.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
    )
}
