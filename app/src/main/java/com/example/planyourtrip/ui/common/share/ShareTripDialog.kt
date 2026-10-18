package com.example.planyourtrip.ui.common.share

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.theme.Spacing

/**
 * Shows the share dialog of [viewModel] and opens the Android share sheet once the PDF is ready. Screens add
 * this once and call `viewModel.onShareTrip(tripId)` from their "Share trip" menu item.
 */
@Composable
fun ShareTripHost(viewModel: ShareTripViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state.dialog?.let { dialog ->
        ShareTripDialog(
            state = dialog,
            onIncludeCostsChange = viewModel::onIncludeCostsChange,
            onCreate = viewModel::onCreatePdf,
            onDismiss = viewModel::onDismiss,
        )
    }
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_trip)
    LaunchedEffect(state.pendingShare) {
        val pdf = state.pendingShare ?: return@LaunchedEffect
        val uri = pdf.uri.toUri()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = pdf.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            // The receiving app may read this one file until its task ends; nothing else in the cache.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle))
        viewModel.onShareLaunched()
    }
}

/** "Include costs" switch (on by default) and "Create PDF"; after a failure a short message and "Try again". */
@Composable
fun ShareTripDialog(
    state: ShareDialogState,
    onIncludeCostsChange: (Boolean) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_trip)) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = state.includeCosts,
                            enabled = !state.isCreating,
                            role = Role.Switch,
                            onValueChange = onIncludeCostsChange,
                        )
                        .padding(vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.share_include_costs),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.share_include_costs_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(Spacing.m))
                    // The whole row toggles; the switch only shows the value.
                    Switch(checked = state.includeCosts, onCheckedChange = null, enabled = !state.isCreating)
                }
                if (state.failed) {
                    Text(
                        text = stringResource(R.string.share_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                }
            }
        },
        confirmButton = {
            PrimaryButton(
                text = stringResource(if (state.failed) R.string.share_try_again else R.string.share_create_pdf),
                onClick = onCreate,
                loading = state.isCreating,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isCreating) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview
@Composable
internal fun ShareTripDialogPreview() {
    PlanYourTripTheme(darkTheme = false) {
        ShareTripDialog(ShareDialogState(tripId = 1), onIncludeCostsChange = {}, onCreate = {}, onDismiss = {})
    }
}

@Preview
@Composable
internal fun ShareTripDialogFailedPreview() {
    PlanYourTripTheme(darkTheme = false) {
        ShareTripDialog(
            ShareDialogState(tripId = 1, includeCosts = false, failed = true),
            onIncludeCostsChange = {}, onCreate = {}, onDismiss = {},
        )
    }
}
