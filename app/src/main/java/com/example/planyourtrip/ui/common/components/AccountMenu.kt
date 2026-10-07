package com.example.planyourtrip.ui.common.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.sync.SyncStatus
import com.example.planyourtrip.ui.common.account.AccountState

/** Callbacks of [AccountButton] (from `AccountViewModel`). */
class AccountActions(
    val onSignOut: () -> Unit,
    val onConfirmSignOut: () -> Unit,
    val onDismissSignOutWarning: () -> Unit,
)

/**
 * The round teal profile button and its menu: the user's name and email, the sync status and "Sign out", plus the
 * "Sign out anyway" warning. Shows the user's initials (Home header).
 */
@Composable
fun AccountButton(
    state: AccountState,
    actions: AccountActions,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (state.showSignOutWarning) {
        SignOutWarningDialog(onConfirm = actions.onConfirmSignOut, onDismiss = actions.onDismissSignOutWarning)
    }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val profileDescription = stringResource(R.string.home_profile, state.userName)
    Box(modifier) {
        Surface(
            onClick = { menuOpen = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = profileDescription },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = state.initials, style = MaterialTheme.typography.titleMedium)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = state.userName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.email.isNotEmpty()) {
                    Text(
                        text = state.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SyncStatusRow(state.syncStatus, modifier = Modifier.padding(top = 8.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_sign_out)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
                onClick = {
                    menuOpen = false
                    actions.onSignOut()
                },
            )
        }
    }
}

/** "All changes saved" / "Syncing…" / "Offline – changes will sync later" with a small cloud icon. */
@Composable
private fun SyncStatusRow(status: SyncStatus, modifier: Modifier = Modifier) {
    val (icon, text) = when (status) {
        SyncStatus.SAVED -> R.drawable.ic_cloud_done to R.string.home_sync_saved
        SyncStatus.SYNCING -> R.drawable.ic_sync to R.string.home_sync_syncing
        SyncStatus.OFFLINE -> R.drawable.ic_cloud_off to R.string.home_sync_offline
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown when changes still wait to upload and there is no connection. */
@Composable
private fun SignOutWarningDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_sign_out_warning_title)) },
        text = { Text(stringResource(R.string.home_sign_out_warning_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.home_sign_out_anyway)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
