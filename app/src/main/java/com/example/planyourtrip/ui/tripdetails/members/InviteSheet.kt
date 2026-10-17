package com.example.planyourtrip.ui.tripdetails.members

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.planyourtrip.R
import com.example.planyourtrip.data.sharing.Invite
import com.example.planyourtrip.data.sharing.InviteCode
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.HeroWaves
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroLabelStyle
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.theme.isLargeFontScale
import com.example.planyourtrip.ui.tripdetails.components.TravelerAvatar
import com.example.planyourtrip.ui.tripdetails.components.personName
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * The members sheet of [viewModel] with its dialogs and snackbars ([snackbarHostState] is the trip screen's).
 * [onLeave] is a non-owner's "Leave trip" (the trip menu's leave flow).
 */
@Composable
fun InviteHost(
    viewModel: InviteViewModel,
    members: MembersState?,
    destination: String,
    snackbarHostState: SnackbarHostState,
    onLeave: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val removedText = stringResource(R.string.member_removed, "%s")
    val unnamed = stringResource(R.string.member_unnamed)
    val removeFailedText = stringResource(R.string.member_remove_failed)
    val cancelFailedText = stringResource(R.string.invite_cancel_failed)
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.onMessageShown()
        snackbarHostState.showSnackbar(
            when (message) {
                is InviteMessage.Removed -> removedText.replace("%s", message.name ?: unnamed)
                InviteMessage.RemoveFailed -> removeFailedText
                InviteMessage.CancelFailed -> cancelFailedText
            },
        )
    }
    val mode = state.sheet
    if (mode != null && members != null) {
        InviteSheet(
            mode = mode,
            state = state,
            members = members,
            destination = destination,
            actions = InviteSheetActions(
                onRetry = viewModel::onRetry,
                onNewCode = viewModel::onNewCode,
                onCancelCode = viewModel::onCancelCode,
                onRemoveMember = viewModel::onRemoveMember,
                onLeave = {
                    viewModel.onDismissSheet()
                    onLeave()
                },
                onDismiss = viewModel::onDismissSheet,
            ),
        )
    }
    state.removing?.let { member ->
        RemoveMemberDialog(
            name = personName(member.person),
            isRemoving = state.isRemoving,
            onConfirm = viewModel::onConfirmRemove,
            onDismiss = viewModel::onDismissRemove,
        )
    }
}

class InviteSheetActions(
    val onRetry: () -> Unit,
    val onNewCode: () -> Unit,
    val onCancelCode: () -> Unit,
    val onRemoveMember: (MemberEntry) -> Unit,
    val onLeave: () -> Unit,
    val onDismiss: () -> Unit,
) {
    companion object {
        val None = InviteSheetActions({}, {}, {}, {}, {}, {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteSheet(
    mode: MembersSheetMode,
    state: InviteUiState,
    members: MembersState,
    destination: String,
    actions: InviteSheetActions,
) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        InviteSheetContent(mode, state, members, destination, actions)
    }
}

/**
 * design/trip_invite.png: with [MembersSheetMode.INVITE] (owner) the code in a large teal card with a copy button,
 * "Expires 16 Oct" and "Works once", "Share code" and "Cancel this code"; then the members list. Members who aren't
 * the owner get "Leave trip" at the end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InviteSheetContent(
    mode: MembersSheetMode,
    state: InviteUiState,
    members: MembersState,
    destination: String,
    actions: InviteSheetActions,
) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding)
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        if (mode == MembersSheetMode.INVITE && members.isOwner) {
            Box(
                modifier = Modifier
                    .size(Dimens.AvatarSize)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_person_add), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.invite_title, destination),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            // "Share this code…" only when there is (or is about to be) a code to share.
            if (state.code is InviteCodeState.Loading || state.code is InviteCodeState.Ready) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.invite_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            CodeSection(state.code, destination, actions)
            Spacer(Modifier.height(32.dp))
        }
        // Without the invite part, "Members" with the count is the sheet's only title.
        MembersList(members, actions, isSheetTitle = mode != MembersSheetMode.INVITE || !members.isOwner)
        if (!members.isOwner) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(
                onClick = actions.onLeave,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.ButtonHeight),
            ) {
                Icon(
                    painterResource(R.drawable.ic_logout),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.trip_leave), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodeSection(code: InviteCodeState, destination: String, actions: InviteSheetActions) {
    when (code) {
        InviteCodeState.Loading -> Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        is InviteCodeState.Ready -> {
            CodeCard(code.invite)
            Spacer(Modifier.height(20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(R.drawable.ic_schedule, stringResource(R.string.invite_expires, TripFormatting.dayMonth(code.expiresOn)))
                InfoPill(R.drawable.ic_shield, stringResource(R.string.invite_works_once))
            }
            Spacer(Modifier.height(24.dp))
            val context = LocalContext.current
            val message = stringResource(
                R.string.invite_share_message,
                destination,
                InviteCode.forMessage(code.invite.code),
                TripFormatting.dayMonth(code.expiresOn),
            )
            val chooserTitle = stringResource(R.string.invite_share)
            PrimaryButton(
                text = stringResource(R.string.invite_share),
                onClick = { shareText(context, message, chooserTitle) },
                leadingIcon = R.drawable.ic_share,
                enabled = !code.isCancelling,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = actions.onCancelCode,
                enabled = !code.isCancelling,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.TouchTarget),
            ) {
                if (code.isCancelling) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.invite_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        InviteCodeState.Cancelled -> StatusWithAction(
            text = stringResource(R.string.invite_cancelled),
            action = stringResource(R.string.invite_new_code),
            onAction = actions.onNewCode,
        )
        InviteCodeState.Failed -> StatusWithAction(
            text = stringResource(R.string.invite_failed),
            action = stringResource(R.string.assistant_try_again),
            onAction = actions.onRetry,
        )
        InviteCodeState.Full -> Text(
            text = stringResource(R.string.invite_full),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusWithAction(text: String, action: String, onAction: () -> Unit) {
    Column {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(text = action, onClick = onAction, modifier = Modifier.fillMaxWidth())
    }
}

/** "INVITE CODE", the code in two groups of four, and the copy button, on the hero gradient. */
@Composable
private fun CodeCard(invite: Invite) {
    val colors = ExtendedTheme.colors
    val shape = MaterialTheme.shapes.extraLarge
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shown = InviteCode.display(invite.code)
    // Read out character by character ("K 7 M 4 X P 2 R").
    val spoken = stringResource(R.string.invite_code_description, invite.code.toCharArray().joinToString(" "))
    val copyLabel = stringResource(R.string.invite_copy)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.heroGradient),
    ) {
        HeroWaves(Modifier.matchParentSize())
        Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.invite_code_label),
                    style = HeroLabelStyle,
                    color = colors.onHeroMuted,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, invite.code)))
                        }
                    },
                    modifier = Modifier
                        .size(Dimens.TouchTarget)
                        .background(colors.heroOverlay, CircleShape),
                ) {
                    Icon(painterResource(R.drawable.ic_content_copy), contentDescription = copyLabel, tint = colors.onHero)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = shown,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.onHero,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp)
                    .clearAndSetSemantics { contentDescription = spoken },
            )
        }
    }
}

@Composable
private fun InfoPill(icon: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * "Members" + count, then a card with one row per member; the owner's ⋮ removes the others. [isSheetTitle]: it is
 * the sheet's title (members only, no invite code above it), so it gets the title size.
 */
@Composable
private fun MembersList(members: MembersState, actions: InviteSheetActions, isSheetTitle: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.members_title),
            style = if (isSheetTitle) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.width(10.dp))
        val countText = pluralStringResource(R.plurals.members_count_description, members.count, members.count)
        Pill(
            text = members.count.toString(),
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { contentDescription = countText },
        )
    }
    Spacer(Modifier.height(if (isSheetTitle) 20.dp else 12.dp))
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
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
                .heightIn(min = 72.dp)
                .padding(start = 16.dp, end = if (onRemove != null) 4.dp else 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            TravelerAvatar(member.person, Dimens.TravelerAvatarSize)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (member.isOwner && !ownerInline) {
                    Spacer(Modifier.height(4.dp))
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
                Spacer(Modifier.width(8.dp))
                OwnerRowPill()
            }
            if (onRemove != null) {
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
        }
    }
}

/** "Remove Noa?" with a red "Remove" (a spinner while it runs). */
@Composable
private fun RemoveMemberDialog(name: String, isRemoving: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.member_remove_title, name)) },
        text = { Text(stringResource(R.string.member_remove_body, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isRemoving) {
                if (isRemoving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
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

/** The Android share sheet with the invite message. */
private fun shareText(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title))
}

private val previewInviteState = InviteUiState(
    sheet = MembersSheetMode.INVITE,
    code = InviteCodeState.Ready(Invite("K7M4XP2R", createdAt = 0), expiresOn = LocalDate.of(2026, 10, 16)),
)

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun InviteSheetPreview() {
    PlanYourTripTheme(darkTheme = false) {
        InviteSheetContent(
            MembersSheetMode.INVITE,
            previewInviteState,
            previewMembers.copy(
                members = previewMembers.members.map {
                    if (it.isOwner) it else it.copy(joinedOn = LocalDate.of(2026, 10, 9))
                },
            ),
            "Rome, Italy",
            InviteSheetActions.None,
        )
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f, heightDp = 1400)
@Composable
private fun InviteSheetNarrowPreview() {
    PlanYourTripTheme(darkTheme = true) {
        InviteSheetContent(MembersSheetMode.INVITE, previewInviteState, previewMembers, "Rome, Italy", InviteSheetActions.None)
    }
}

@Preview(showBackground = true)
@Composable
private fun MembersSheetMemberPreview() {
    PlanYourTripTheme(darkTheme = false) {
        InviteSheetContent(
            MembersSheetMode.MEMBERS,
            InviteUiState(sheet = MembersSheetMode.MEMBERS),
            MembersState(
                me = "noa",
                members = listOf(
                    MemberEntry(Person.You("noa"), isOwner = false, joinedOn = LocalDate.of(2026, 10, 9)),
                    MemberEntry(
                        Person.Member("me", "Amit", com.example.planyourtrip.data.trip.TravelerColor.GREEN_TEAL, isOwner = true),
                        isOwner = true,
                        joinedOn = null,
                    ),
                ),
                isOwner = false,
            ),
            "Rome, Italy",
            InviteSheetActions.None,
        )
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
