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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.TripStatus
import com.example.planyourtrip.ui.common.components.DestinationTitle
import com.example.planyourtrip.ui.common.components.HeroWaves
import com.example.planyourtrip.ui.common.components.TripDatesRow
import com.example.planyourtrip.ui.common.components.TripMoreButton
import com.example.planyourtrip.ui.common.datesWithLength
import com.example.planyourtrip.ui.home.ActiveTrip
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroLabelStyle

/** Large teal gradient card for the nearest (or ongoing) trip. */
@Composable
fun NextTripCard(
    next: ActiveTrip,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** False for a shared trip someone else owns: the menu offers "Leave trip". */
    isOwner: Boolean = true,
) {
    val colors = ExtendedTheme.colors
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.heroGradient)
            .clickable(onClick = onClick),
    ) {
        HeroWaves(Modifier.matchParentSize())
        Row(
            modifier = Modifier.padding(start = 24.dp, end = Dimens.CardMenuEndPadding, top = 24.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(colors.heroLiveDot, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.home_next_trip),
                        style = HeroLabelStyle,
                        color = colors.onHeroMuted,
                    )
                }
                Spacer(Modifier.height(16.dp))
                DestinationTitle(
                    destination = next.trip.destination,
                    countryCode = next.trip.countryCode,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onHero,
                    maxLines = 3,
                )
                Spacer(Modifier.height(16.dp))
                TripDatesRow(
                    text = datesWithLength(next.trip),
                    color = colors.onHeroMuted,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.width(12.dp))
            CountdownBox(next.status)
            // The button's own inset gives the gap to the countdown box.
            TripMoreButton(next.trip.destination, onShare, onDelete, tint = colors.onHeroMuted, isOwner = isOwner)
        }
    }
}

@Composable
private fun CountdownBox(status: TripStatus) {
    val colors = ExtendedTheme.colors
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = Modifier
            .widthIn(min = 92.dp)
            .clip(shape)
            .background(colors.heroOverlay)
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
                    color = colors.onHeroMuted,
                    maxLines = 1,
                )
            }
        }
    }
}
