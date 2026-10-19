package com.example.planyourtrip.ui.home.join

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.sharing.InvitePreview
import com.example.planyourtrip.ui.common.components.HeroWaves
import com.example.planyourtrip.ui.common.datesWithLength
import com.example.planyourtrip.ui.common.split.avatarLetter
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroLabelStyle
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing

/** "YOU'RE JOINING": the invite's trip on the hero gradient, with who invited me in the band at the bottom. */
@Composable
internal fun JoiningCard(preview: InvitePreview) {
    val colors = ExtendedTheme.colors
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.heroGradient)
            .semantics(mergeDescendants = true) {},
    ) {
        HeroWaves(Modifier.matchParentSize())
        Column {
            JoiningTitle(preview)
            InvitedByBand(preview.inviterName)
        }
    }
}

/** "YOU'RE JOINING" with the valid-code badge, then the destination and dates. */
@Composable
private fun JoiningTitle(preview: InvitePreview) {
    val colors = ExtendedTheme.colors
    Column(Modifier.padding(start = Spacing.xl, end = Spacing.l, top = Spacing.l, bottom = Spacing.l)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.join_joining_label),
                style = HeroLabelStyle,
                color = colors.onHeroMuted,
                modifier = Modifier.weight(1f),
            )
            ValidCodeBadge()
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            text = preview.destination,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = colors.onHero,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = datesWithLength(preview.startDate, preview.endDate),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onHeroMuted,
        )
    }
}

/** "✓ Valid code" on the hero. */
@Composable
private fun ValidCodeBadge() {
    val colors = ExtendedTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(colors.heroOverlay, CircleShape)
            .border(Dimens.Hairline, colors.heroOverlayBorder, CircleShape)
            .padding(horizontal = Spacing.sPlus, vertical = Spacing.xs),
    ) {
        Icon(
            painterResource(R.drawable.ic_verified),
            contentDescription = null,
            tint = colors.onHero,
            modifier = Modifier.size(IconSize.xs),
        )
        Spacer(Modifier.width(Spacing.xsPlus))
        Text(
            text = stringResource(R.string.join_valid_code),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onHero,
        )
    }
}

/** The band at the bottom: who invited me, with their letter. */
@Composable
private fun InvitedByBand(inviterName: String) {
    val colors = ExtendedTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.heroOverlay)
            .padding(horizontal = Spacing.xl, vertical = Spacing.m),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(OverlayBadgeSize)
                .background(MaterialTheme.colorScheme.inverseSurface, CircleShape),
        ) {
            Text(
                text = avatarLetter(inviterName),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Text(
            text = stringResource(R.string.join_invited_by, inviterName),
            style = MaterialTheme.typography.titleSmall,
            color = colors.onHero,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The round badge at the start of the trip preview's bottom row. */
private val OverlayBadgeSize = 28.dp
