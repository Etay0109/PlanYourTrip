package com.example.planyourtrip.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.LetterSquare
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.common.components.TripDatesRow
import com.example.planyourtrip.ui.common.components.TripMoreButton
import com.example.planyourtrip.ui.common.datesWithLength
import com.example.planyourtrip.ui.common.statusLabel
import com.example.planyourtrip.ui.home.ActiveTrip
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme

/**
 * White card for an upcoming trip. On narrow cards or large font scales the countdown
 * badge moves under the dates so the destination and dates keep enough width.
 */
@Composable
fun UpcomingTripCard(
    item: ActiveTrip,
    onClick: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ExtendedTheme.colors
    val pastel = colors.pastel(item.trip.color)
    BoxWithConstraints(modifier) {
        val badgeInline = maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.2f
        val badge = statusLabel(item.status)
        TripListCard(
            destination = item.trip.destination,
            dates = datesWithLength(item.trip),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 1.dp,
            letterContainer = pastel.container,
            letterContent = pastel.content,
            titleStyle = MaterialTheme.typography.titleLarge,
            titleColor = MaterialTheme.colorScheme.onSurface,
            datesColor = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onClick,
            onShare = onShare,
            belowDates = {
                if (!badgeInline) {
                    Spacer(Modifier.height(8.dp))
                    Pill(badge, colors.badgeContainer, colors.onBadge)
                }
            },
            beforeMenu = {
                if (badgeInline) {
                    Spacer(Modifier.width(12.dp))
                    Pill(badge, colors.badgeContainer, colors.onBadge)
                }
            },
        )
    }
}

/** Muted gray card for a trip that has ended. Shows month and year only, no countdown. */
@Composable
fun PastTripCard(
    trip: Trip,
    onClick: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ExtendedTheme.colors
    TripListCard(
        destination = trip.destination,
        dates = TripFormatting.monthYear(trip.startDate),
        containerColor = colors.pastCardContainer,
        border = BorderStroke(1.dp, colors.pastCardBorder),
        letterContainer = colors.pastLetterContainer,
        letterContent = colors.onPastCard,
        titleStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
        titleColor = colors.onPastCard,
        datesColor = colors.onPastCardMuted,
        onClick = onClick,
        onShare = onShare,
        modifier = modifier,
    )
}

/**
 * Shared skeleton of the list cards: letter square, destination, dates and the three-dots menu at the right edge.
 * [belowDates] and [beforeMenu] hold optional extras such as the countdown badge.
 */
@Composable
private fun TripListCard(
    destination: String,
    dates: String,
    containerColor: Color,
    letterContainer: Color,
    letterContent: Color,
    titleStyle: TextStyle,
    titleColor: Color,
    datesColor: Color,
    onClick: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    belowDates: @Composable ColumnScope.() -> Unit = {},
    beforeMenu: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        border = border,
        shadowElevation = shadowElevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = Dimens.CardMenuEndPadding, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LetterSquare(destination, letterContainer, letterContent)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = destination,
                    style = titleStyle,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                TripDatesRow(
                    text = dates,
                    color = datesColor,
                    style = MaterialTheme.typography.bodyLarge,
                )
                belowDates()
            }
            beforeMenu()
            // The button's own inset gives the gap to the badge or the text.
            TripMoreButton(destination, onShare, tint = ExtendedTheme.colors.chevron)
        }
    }
}
