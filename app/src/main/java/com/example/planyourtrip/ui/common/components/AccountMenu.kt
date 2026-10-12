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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.sync.SyncStatus
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.rememberNotificationPermissionRequest
import com.example.planyourtrip.ui.common.account.AccountState
import com.example.planyourtrip.ui.common.account.SignOutWarning
import java.time.ZoneId

/** Callbacks of [AccountButton] (from `AccountViewModel`). */
class AccountActions(
    val onSignOut: () -> Unit,
    val onConfirmSignOut: () -> Unit,
    val onDismissSignOutWarning: () -> Unit,
    /** "Sync problem – tap to retry". */
    val onRetrySync: () -> Unit = {},
    /** The "Trip reminders" switch. */
    val onRemindersChanged: (Boolean) -> Unit = {},
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
    state.signOutWarning?.let { warning ->
        SignOutWarningDialog(warning, onConfirm = actions.onConfirmSignOut, onDismiss = actions.onDismissSignOutWarning)
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
                if (state.syncStatus != SyncStatus.PROBLEM) {
                    SyncStatusRow(state.syncStatus, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (state.syncStatus == SyncStatus.PROBLEM) {
                SyncProblemItem(state.lastSyncedAt, isRetrying = state.isRetrying, onRetry = actions.onRetrySync)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            RemindersItem(enabled = state.remindersEnabled, onChange = actions.onRemindersChanged)
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

/**
 * "Trip reminders" with a switch; the whole row toggles. Turning it on also asks for the notification permission
 * when Android 13+ still needs it (a direct choice, so it is asked even if it was asked before).
 */
@Composable
private fun RemindersItem(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val requestPermission = rememberNotificationPermissionRequest()
    val toggle = {
        val on = !enabled
        onChange(on)
        if (on) requestPermission()
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.home_trip_reminders)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_notifications), contentDescription = null) },
        trailingIcon = { Switch(checked = enabled, onCheckedChange = null) },
        onClick = { toggle() },
        modifier = Modifier.semantics {
            role = Role.Switch
            toggleableState = ToggleableState(enabled)
        },
    )
}

/** "All changes saved" / "Syncing…" / "Offline – changes will sync later" with a small cloud icon. */
@Composable
private fun SyncStatusRow(status: SyncStatus, modifier: Modifier = Modifier) {
    val (icon, text) = when (status) {
        SyncStatus.SAVED -> R.drawable.ic_cloud_done to R.string.home_sync_saved
        SyncStatus.SYNCING -> R.drawable.ic_sync to R.string.home_sync_syncing
        SyncStatus.OFFLINE, SyncStatus.PROBLEM -> R.drawable.ic_cloud_off to R.string.home_sync_offline
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

/**
 * "Sync problem – tap to retry" in the error color, with the time of the last successful sync under it; a menu row
 * (48dp touch target) that starts a full sync. While it runs, the row reads "Syncing…" and ignores taps.
 */
@Composable
private fun SyncProblemItem(lastSyncedAt: Long?, isRetrying: Boolean, onRetry: () -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    val last = lastSyncedAt?.let { stringResource(R.string.home_sync_last, TripFormatting.dayMonthTime(it, zone)) }
        ?: stringResource(R.string.home_sync_never)
    val description = stringResource(R.string.home_sync_problem_a11y, last)
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = stringResource(if (isRetrying) R.string.home_sync_syncing else R.string.home_sync_problem),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = last,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        leadingIcon = {
            Icon(
                painter = painterResource(if (isRetrying) R.drawable.ic_sync else R.drawable.ic_cloud_off),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        onClick = { if (!isRetrying) onRetry() },
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    )
}

/** Shown when signing out would keep the account's data on this device ([SignOutWarning] says why). */
@Composable
private fun SignOutWarningDialog(warning: SignOutWarning, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, body) = when (warning) {
        SignOutWarning.PENDING_CHANGES -> R.string.home_sign_out_warning_title to R.string.home_sign_out_warning_body
        SignOutWarning.NOT_CONFIRMED -> R.string.home_sign_out_unconfirmed_title to R.string.home_sign_out_unconfirmed_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.home_sign_out_anyway)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
