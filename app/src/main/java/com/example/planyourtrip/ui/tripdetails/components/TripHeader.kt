package com.example.planyourtrip.ui.tripdetails.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.ui.common.TripStatus
import com.example.planyourtrip.ui.common.components.HeroWaves
import com.example.planyourtrip.ui.common.components.TripDatesRow
import com.example.planyourtrip.ui.common.datesWithLength
import com.example.planyourtrip.ui.common.statusLabel
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme

/**
 * Teal gradient header of the trip screen: back, countdown badge, edit, destination and dates.
 * It draws behind the status bar (the app is edge-to-edge) and pads its content below it.
 */
@Composable
fun TripHeader(
    trip: Trip,
    status: TripStatus?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ExtendedTheme.colors
    val shape = MaterialTheme.shapes.extraLarge.copy(topStart = CornerSize(0.dp), topEnd = CornerSize(0.dp))
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.heroGradient),
    ) {
        HeroWaves(Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 12.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onBack)
                Spacer(Modifier.weight(1f))
                if (status != null) {
                    StatusBadge(statusLabel(status))
                    Spacer(Modifier.width(8.dp))
                }
                HeaderButton(R.drawable.ic_edit, stringResource(R.string.trip_edit), onEdit)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = trip.destination,
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onHero,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            TripDatesRow(
                text = datesWithLength(trip),
                color = colors.onHeroMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun HeaderButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    val colors = ExtendedTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = colors.heroOverlay,
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
private fun StatusBadge(text: String) {
    val colors = ExtendedTheme.colors
    Row(
        modifier = Modifier
            .background(colors.heroOverlay, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(colors.onHeroMuted, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = colors.onHero, maxLines = 1)
    }
}
