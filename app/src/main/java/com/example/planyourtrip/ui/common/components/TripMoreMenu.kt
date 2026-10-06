package com.example.planyourtrip.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.theme.Dimens

/**
 * The three-dots menu of a trip ("Share trip"). [anchor] draws the button that opens it, so the home cards and
 * the trip header can each use their own button style. [menuOffset] shifts the menu from its anchor.
 */
@Composable
fun TripMoreMenu(
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    menuOffset: DpOffset = DpOffset.Zero,
    anchor: @Composable (open: () -> Unit) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
        anchor { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, offset = menuOffset) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_trip)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_share), contentDescription = null) },
                onClick = {
                    open = false
                    onShare()
                },
            )
        }
    }
}

/**
 * Three-dots button at the right edge of the home trip cards: a 20dp icon without background in a 48dp touch
 * target. The card row ends with [Dimens.CardMenuEndPadding], so the icon sits [Dimens.CardMenuIconInset] from the
 * card's edge, and the menu (right-aligned to the button) is shifted by that padding to line up with the card's
 * edge. The button consumes its taps, so they never open the trip.
 */
@Composable
fun TripMoreButton(
    destination: String,
    onShare: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    TripMoreMenu(onShare, modifier, menuOffset = DpOffset(Dimens.CardMenuEndPadding, 0.dp)) { open ->
        IconButton(onClick = open, modifier = Modifier.size(Dimens.TouchTarget)) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.share_more_options, destination),
                tint = tint,
                modifier = Modifier.size(Dimens.CardMenuIconSize),
            )
        }
    }
}
