package com.example.planyourtrip.ui.tripdetails.members

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
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
        shadowElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = cardDescription },
    ) {
        Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
                Text(
                    text = stringResource(R.string.members_card_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .semantics { heading() },
                )
                Spacer(Modifier.width(10.dp))
                Pill(
                    text = count.toString(),
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = countText },
                )
                Spacer(Modifier.weight(1f))
                if (state.isOwner) {
                    Spacer(Modifier.width(8.dp))
                    InviteButton(onInvite)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                state.members.forEach { MemberAvatarItem(it) }
            }
        }
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
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier,
    ) {
        Icon(painterResource(R.drawable.ic_person_add), contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.members_invite))
    }
}

/** One avatar with the name under it, and "Owner" under the owner. */
@Composable
private fun MemberAvatarItem(member: MemberEntry) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(MemberItemWidth),
    ) {
        TravelerAvatar(member.person, Dimens.TravelerAvatarSize)
        Spacer(Modifier.height(8.dp))
        Text(
            text = personName(member.person),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (member.isOwner) {
            Spacer(Modifier.height(4.dp))
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
private fun MembersCardPreview() {
    PlanYourTripTheme(darkTheme = false) {
        MembersCard(previewMembers, onShowMembers = {}, onInvite = {}, modifier = Modifier.padding(20.dp))
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
private fun MembersCardNarrowPreview() {
    PlanYourTripTheme(darkTheme = true) {
        MembersCard(previewMembers, onShowMembers = {}, onInvite = {}, modifier = Modifier.padding(20.dp))
    }
}
