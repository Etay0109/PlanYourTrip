package com.example.planyourtrip.ui.common.delete

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.theme.PlanYourTripTheme

/**
 * Shows the "Delete this trip?" dialog of [viewModel]. [onConfirm] is the red "Delete" button: Home deletes right
 * away (`viewModel.onConfirm()`, the default), the trip screen hands the trip to Home. Snackbars are the screen's.
 */
@Composable
fun DeleteTripHost(viewModel: DeleteTripViewModel, onConfirm: () -> Unit = viewModel::onConfirm) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    state.dialog?.let { dialog ->
        DeleteTripDialog(state = dialog, onConfirm = onConfirm, onDismiss = viewModel::onDismiss)
    }
}

/**
 * Names the trip and what goes with it; "Cancel" and a red "Delete" (a small spinner while the delete runs). For a
 * shared trip I don't own: "Leave this trip?" with a red "Leave".
 */
@Composable
fun DeleteTripDialog(state: DeleteDialogState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (state.leave) R.string.trip_leave_title else R.string.trip_delete_title)) },
        text = {
            Text(stringResource(if (state.leave) R.string.trip_leave_body else R.string.trip_delete_body, state.destination))
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.isDeleting) {
                if (state.isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        stringResource(if (state.leave) R.string.action_leave else R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isDeleting) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun DeleteTripDialogPreview() {
    PlanYourTripTheme(darkTheme = false) {
        DeleteTripDialog(DeleteDialogState(tripId = 1, destination = "Rome, Italy"), onConfirm = {}, onDismiss = {})
    }
}

@Preview
@Composable
private fun LeaveTripDialogPreview() {
    PlanYourTripTheme(darkTheme = false) {
        DeleteTripDialog(DeleteDialogState(tripId = 1, destination = "Rome, Italy", leave = true), onConfirm = {}, onDismiss = {})
    }
}

@Preview
@Composable
private fun DeleteTripDialogHebrewPreview() {
    PlanYourTripTheme(darkTheme = false) {
        DeleteTripDialog(DeleteDialogState(tripId = 1, destination = "תל אביב"), onConfirm = {}, onDismiss = {})
    }
}
