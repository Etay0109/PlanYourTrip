package com.example.planyourtrip.ui.tripdetails.members

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.theme.isLargeFontScale
import com.example.planyourtrip.ui.tripdetails.components.TravelerAvatar
import com.example.planyourtrip.ui.tripdetails.components.personName

/**
 * "Members" + count, then a card with one row per member; the owner's ⋮ removes the others. [isSheetTitle]: it is
 * the sheet's title (members only, no invite code above it), so it gets the title size.
 */
@Composable
internal fun MembersList(members: MembersState, actions: InviteSheetActions, isSheetTitle: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.members_title),
            style = if (isSheetTitle) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.width(Spacing.sPlus))
        val countText = pluralStringResource(R.plurals.members_count_description, members.count, members.count)
        Pill(
            text = members.count.toString(),
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { contentDescription = countText },
        )
    }
    Spacer(Modifier.height(if (isSheetTitle) Spacing.xl else Spacing.m))
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(Dimens.Hairline, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        members.members.forEachIndexed { index, member ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            MemberRow(
                member = member,
                onRemove = if (members.isOwner && member.person !is Person.You) {
                    { actions.onRemoveMember(member) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun MemberRow(member: MemberEntry, onRemove: (() -> Unit)?) {
    val name = personName(member.person)
    // On narrow sheets or large fonts "Owner" goes under the name, so the name keeps the row's width.
    BoxWithConstraints {
        val ownerInline = maxWidth >= Dimens.NarrowContentWidth && !isLargeFontScale()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MemberRowMinHeight)
                .padding(start = Spacing.l, end = if (onRemove != null) Spacing.xs else Spacing.l, top = Spacing.s, bottom = Spacing.s),
        ) {
            TravelerAvatar(member.person, Dimens.TravelerAvatarSize)
            Spacer(Modifier.width(Spacing.l))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.isOwner && !ownerInline) {
                    Spacer(Modifier.height(Spacing.xs))
                    OwnerRowPill()
                }
                val joined = member.joinedOn
                if (!member.isOwner && joined != null) {
                    Text(
                        text = stringResource(R.string.member_joined, TripFormatting.dayMonth(joined)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (member.isOwner && ownerInline) {
                Spacer(Modifier.width(Spacing.s))
                OwnerRowPill()
            }
            if (onRemove != null) MemberMenu(name, onRemove)
        }
    }
}

/** Three dots on another member's row (owner only): "Remove from trip". */
@Composable
private fun MemberMenu(name: String, onRemove: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.member_more_options, name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.member_remove), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

/** "Owner" in the members list. */
@Composable
private fun OwnerRowPill() {
    Pill(
        text = stringResource(R.string.member_owner),
        container = MaterialTheme.colorScheme.primaryContainer,
        content = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

/** "Remove Noa?" with a red "Remove" (a spinner while it runs). */
@Composable
internal fun RemoveMemberDialog(name: String, isRemoving: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.member_remove_title, name)) },
        text = { Text(stringResource(R.string.member_remove_body, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isRemoving) {
                if (isRemoving) {
                    CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = Dimens.ProgressStroke, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(stringResource(R.string.member_remove), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRemoving) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val MemberRowMinHeight = 72.dp
