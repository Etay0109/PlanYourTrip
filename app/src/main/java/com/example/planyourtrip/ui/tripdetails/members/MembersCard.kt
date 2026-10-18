package com.example.planyourtrip.ui.tripdetails.members

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.minFitTextScale
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.tripdetails.components.TravelerAvatar
import com.example.planyourtrip.ui.tripdetails.components.personName

/** What the "Traveling together" card can do: open the members list, or (owner) the invite sheet. */
class MemberActions(val onShowMembers: () -> Unit, val onInvite: () -> Unit) {
    companion object {
        val None = MemberActions({}, {})
    }
}

/**
 * The Overview's "Traveling together" card (design/trip_members.png, only this card): title with the member count,
 * the members' avatars ("You" first, "Owner" under the owner) and, for the owner only, "Invite". Tapping the card
 * opens the members list.
 */
@Composable
fun MembersCard(
    state: MembersState,
    onShowMembers: () -> Unit,
    onInvite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = state.count
    val countText = pluralStringResource(R.plurals.members_count_description, count, count)
    val cardDescription = stringResource(R.string.members_card_description, countText)
    Surface(
        onClick = onShowMembers,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = Dimens.CardElevation,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription },
    ) {
        Column(Modifier.padding(start = Spacing.xl, end = Spacing.l, top = Spacing.l, bottom = Spacing.xl)) {
            MembersCardTitleRow(count, countText, state.isOwner, onInvite)
            Spacer(Modifier.height(Spacing.m))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                state.members.forEach { MemberAvatarItem(it) }
            }
        }
    }
}

/**
 * "Traveling together", the count pill and (owner) "Invite", always on one line: when they don't all fit, "Invite"
 * becomes a round icon button (same action, TalkBack says "Invite"); on the narrowest screens the title also gets a
 * smaller size, so it never wraps.
 */
@Composable
private fun MembersCardTitleRow(count: Int, countText: String, isOwner: Boolean, onInvite: () -> Unit) {
    val title = stringResource(R.string.members_card_title)
    val inviteLabel = stringResource(R.string.members_invite)
    val titleStyle = MaterialTheme.typography.titleLarge
    val pillStyle = MaterialTheme.typography.labelMedium
    val inviteStyle = MaterialTheme.typography.labelLarge
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val available = constraints.maxWidth
        val minScale = minFitTextScale(density.fontScale)
        val layout = remember(title, inviteLabel, count, isOwner, titleStyle, available, density) {
            fun width(text: String, style: TextStyle) =
                measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
            with(density) {
                val pill = width(count.toString(), pillStyle) + PillPadding.roundToPx()
                val fixed = TitleGap.roundToPx() + pill
                val fullInvite = if (isOwner) {
                    InviteGap.roundToPx() + width(inviteLabel, inviteStyle) + InviteButtonExtra.roundToPx()
                } else {
                    0
                }
                val iconInvite = if (isOwner) InviteGap.roundToPx() + Dimens.TouchTarget.roundToPx() else 0
                val titleWidth = width(title, titleStyle)
                when {
                    titleWidth + fixed + fullInvite <= available -> TitleRowLayout(compactInvite = false, titleStyle)
                    else -> {
                        // Shrink the title only as much as needed (see minFitTextScale).
                        val room = available - fixed - iconInvite
                        // Text width doesn't scale exactly with the size: check it and step down until it fits.
                        fun scaled(scale: Float) = titleStyle.copy(fontSize = titleStyle.fontSize * scale)
                        var scale = (room.toFloat() / titleWidth).coerceIn(minScale, 1f)
                        while (scale > minScale && width(title, scaled(scale)) > room) {
                            scale = (scale - 0.02f).coerceAtLeast(minScale)
                        }
                        val style = if (scale < 1f) scaled(scale) else titleStyle
                        TitleRowLayout(compactInvite = isOwner, style)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
            // Title and count stay together on the left; "Invite" sits at the right edge.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = layout.titleStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { heading() },
                )
                Spacer(Modifier.width(TitleGap))
                Pill(
                    text = count.toString(),
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = countText },
                )
            }
            if (isOwner) {
                Spacer(Modifier.width(InviteGap))
                if (layout.compactInvite) InviteIconButton(onInvite) else InviteButton(onInvite)
            }
        }
    }
}

private data class TitleRowLayout(val compactInvite: Boolean, val titleStyle: TextStyle)

/** Gaps and paddings of the title row (they decide when "Invite" turns into an icon). */
private val TitleGap = Spacing.sPlus
private val InviteGap = Spacing.s
private val PillPadding = Spacing.xxlPlus
/** The button's horizontal padding (2 × 16), icon (18) and icon gap (8), plus a little slack. */
private val InviteButtonExtra = 66.dp

/** "Invite" as a round tonal icon button, for narrow cards. */
@Composable
private fun InviteIconButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.size(Dimens.TouchTarget),
    ) {
        Icon(
            painterResource(R.drawable.ic_person_add),
            contentDescription = stringResource(R.string.members_invite),
            modifier = Modifier.size(IconSize.m),
        )
    }
}

/** The tonal "Invite" pill (owner only). */
@Composable
internal fun InviteButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        contentPadding = PaddingValues(horizontal = Spacing.l),
        modifier = modifier,
    ) {
        Icon(painterResource(R.drawable.ic_person_add), contentDescription = null, modifier = Modifier.size(IconSize.s))
        Spacer(Modifier.width(Spacing.s))
        Text(stringResource(R.string.members_invite))
    }
}

/** One avatar with the name under it, and "Owner" under the owner. */
@Composable
private fun MemberAvatarItem(member: MemberEntry) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // At least the item width; wider only when "Owner" needs it (large fonts). Names keep the item width.
        modifier = Modifier.widthIn(min = MemberItemWidth),
    ) {
        TravelerAvatar(member.person, Dimens.TravelerAvatarSize)
        Spacer(Modifier.height(Spacing.s))
        Text(
            text = personName(member.person),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = MemberItemWidth),
        )
        if (member.isOwner) {
            Spacer(Modifier.height(Spacing.xs))
            OwnerPill()
        }
    }
}

/** The small "Owner" label. */
@Composable
internal fun OwnerPill(modifier: Modifier = Modifier) {
    Pill(
        text = stringResource(R.string.member_owner),
        container = MaterialTheme.colorScheme.surfaceContainerHighest,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private val MemberItemWidth = 76.dp

internal val previewMembers = MembersState(
    me = "me",
    members = listOf(
        MemberEntry(Person.You("me"), isOwner = true, joinedOn = null),
        MemberEntry(Person.Member("noa", "Noa", TravelerColor.ROSE), isOwner = false, joinedOn = null),
    ),
    isOwner = true,
)

@Preview(showBackground = true)
@Composable
internal fun MembersCardPreview() {
    PlanYourTripTheme(darkTheme = false) {
        MembersCard(previewMembers, onShowMembers = {}, onInvite = {}, modifier = Modifier.padding(Spacing.xl))
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
internal fun MembersCardNarrowPreview() {
    PlanYourTripTheme(darkTheme = true) {
        MembersCard(previewMembers, onShowMembers = {}, onInvite = {}, modifier = Modifier.padding(Spacing.xl))
    }
}
