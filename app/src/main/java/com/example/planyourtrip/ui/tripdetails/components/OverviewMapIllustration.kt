package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.theme.isLargeFontScale

/**
 * The Overview Map card's picture: a simple drawn map (land, a river, a few streets, a teal pin in the middle) with
 * the "Open in Google Maps" pill under the pin. Static and offline: no tiles, no search, no location. It never
 * takes touches itself; the card opens Google Maps on the destination.
 */
@Composable
fun OverviewMapIllustration(modifier: Modifier = Modifier) {
    val land = MaterialTheme.colorScheme.surfaceContainerHighest
    val street = MaterialTheme.colorScheme.surfaceContainerLowest
    val water = MaterialTheme.colorScheme.primaryContainer
    val park = MaterialTheme.colorScheme.secondaryContainer
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = Dimens.MapPreviewMinHeight)
            .clip(MaterialTheme.shapes.large)
            .background(land),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            // A park in one corner.
            drawCircle(park, radius = w * 0.22f, center = Offset(w * 0.9f, h * 0.15f))
            // A river across the lower part.
            val river = Path().apply {
                moveTo(0f, h * 0.78f)
                cubicTo(w * 0.3f, h * 0.62f, w * 0.6f, h * 1.0f, w, h * 0.8f)
            }
            drawPath(river, water, style = Stroke(width = RiverWidth.toPx(), cap = StrokeCap.Round))
            // Streets: two long ones and a few short ones.
            val main = MainRoadWidth.toPx()
            val side = SideRoadWidth.toPx()
            drawLine(street, Offset(0f, h * 0.32f), Offset(w, h * 0.42f), main, StrokeCap.Round)
            drawLine(street, Offset(w * 0.36f, 0f), Offset(w * 0.28f, h), main, StrokeCap.Round)
            drawLine(street, Offset(w * 0.68f, 0f), Offset(w * 0.74f, h * 0.7f), side, StrokeCap.Round)
            drawLine(street, Offset(0f, h * 0.1f), Offset(w * 0.32f, h * 0.2f), side, StrokeCap.Round)
            drawLine(street, Offset(w * 0.45f, h * 0.62f), Offset(w, h * 0.55f), side, StrokeCap.Round)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Spacing.s)) {
            Icon(
                painterResource(R.drawable.ic_location),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.xxl),
            )
            Spacer(Modifier.height(Spacing.xsPlus))
            OpenInMapsPill()
        }
    }
}

/**
 * The white pill with "Open in Google Maps" (and a map icon). On narrow phones (< 360dp) or above font scale
 * 1.2 the card is too narrow for both: the icon goes and the text wraps. (Measured from the window, not with
 * BoxWithConstraints: the card row asks for intrinsic heights, which subcomposition can't answer.)
 */
@Composable
private fun OpenInMapsPill(modifier: Modifier = Modifier) {
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val compact = windowWidth < Dimens.CompactScreenWidth || isLargeFontScale()
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = PillElevation,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Spacing.sPlus, vertical = Spacing.xsPlus),
        ) {
            if (!compact) {
                Icon(
                    painterResource(R.drawable.ic_map),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.xs),
                )
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(
                text = stringResource(R.string.trip_map_open_google),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(name = "Map illustration", widthDp = 180, heightDp = 120)
@Composable
internal fun OverviewMapIllustrationPreview() {
    PlanYourTripTheme { OverviewMapIllustration() }
}

@Preview(name = "Map illustration – dark", widthDp = 180, heightDp = 120)
@Composable
internal fun OverviewMapIllustrationDarkPreview() {
    PlanYourTripTheme(darkTheme = true) { OverviewMapIllustration() }
}

/** The drawn map: river and road widths. */
private val RiverWidth = 10.dp
private val MainRoadWidth = 5.dp
private val SideRoadWidth = 3.dp

/** The "Open in Maps" pill's shadow over the map. */
private val PillElevation = 2.dp
