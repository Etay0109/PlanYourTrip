package com.example.planyourtrip.ui.tripdetails.members

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.tripdetails.components.personName

/**
 * The members sheet of [viewModel] with its dialogs and snackbars: inside the sheet while it is open (the trip screen's
 * would be hidden behind it), otherwise on [snackbarHostState], the trip screen's. [onLeave] is a non-owner's "Leave
 * trip" (the trip menu's leave flow).
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
    val sheetSnackbarHostState = remember { SnackbarHostState() }
    val sheetShown = state.sheet != null && members != null
    // Cleared after the snackbar (clearing first changes the key and cancels it), only if no newer message replaced it.
    // Closing the sheet ends a message shown in it (its host is gone, so it would never time out).
    LaunchedEffect(state.message, sheetShown) {
        val message = state.message ?: return@LaunchedEffect
        val host = if (sheetShown) sheetSnackbarHostState else snackbarHostState
        try {
            host.showSnackbar(
                when (message) {
                    is InviteMessage.Removed -> removedText.replace("%s", message.name ?: unnamed)
                    InviteMessage.RemoveFailed -> removeFailedText
                    InviteMessage.CancelFailed -> cancelFailedText
                },
            )
        } finally {
            if (state.message == message) viewModel.onMessageShown()
        }
    }
    val mode = state.sheet
    if (mode != null && members != null) {
        InviteSheet(
            mode = mode,
            state = state,
            members = members,
            destination = destination,
            snackbarHostState = sheetSnackbarHostState,
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
    snackbarHostState: SnackbarHostState,
    actions: InviteSheetActions,
) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Box {
            InviteSheetContent(mode, state, members, destination, actions)
            // The sheet's own messages, over its bottom edge.
            SnackbarHost(
                snackbarHostState,
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
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
            .padding(bottom = Spacing.l),
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
            Spacer(Modifier.height(Spacing.l))
            Text(
                text = stringResource(R.string.invite_title, destination),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            // "Share this code…" only when there is (or is about to be) a code to share.
            if (state.code is InviteCodeState.Loading || state.code is InviteCodeState.Ready) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.invite_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.xxl))
            CodeSection(state.code, destination, actions)
            Spacer(Modifier.height(Spacing.xxxl))
        }
        // Without the invite part, "Members" with the count is the sheet's only title.
        MembersList(members, actions, isSheetTitle = mode != MembersSheetMode.INVITE || !members.isOwner)
        if (!members.isOwner) {
            Spacer(Modifier.height(Spacing.xl))
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
                    modifier = Modifier.size(IconSize.m),
                )
                Spacer(Modifier.width(Spacing.s))
                Text(stringResource(R.string.trip_leave), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
