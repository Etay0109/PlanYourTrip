package com.example.planyourtrip.ui.tripdetails.split

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.TextLimits
import com.example.planyourtrip.data.trip.limitTo
import com.example.planyourtrip.ui.common.components.ConfirmDeleteDialog
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.common.split.TravelerNameError
import com.example.planyourtrip.ui.theme.Spacing

/** The traveler dialog, the "Settle up" sheet and the delete-payment confirmation of [viewModel]. */
@Composable
fun SplitDialogs(viewModel: SplitViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loaded = state as? SplitUiState.Loaded ?: return

    val travelerDialog by viewModel.travelerDialog.collectAsStateWithLifecycle()
    travelerDialog?.let { dialog ->
        TravelerDialog(
            state = dialog,
            onNameChange = viewModel::onTravelerNameChange,
            onSave = viewModel::onSaveTraveler,
            onRemove = viewModel::onRemoveTraveler,
            onDismiss = viewModel::onDismissTravelerDialog,
        )
    }

    val sheet by viewModel.settleSheet.collectAsStateWithLifecycle()
    sheet?.let {
        SettleUpSheet(
            sheet = it,
            state = loaded,
            onSelect = viewModel::onSelectTransfer,
            onAmountChange = viewModel::onSettleAmountChange,
            onMarkAsPaid = viewModel::onMarkAsPaid,
            onShowHistory = viewModel::onShowHistory,
            onShowTransfers = viewModel::onShowTransfers,
            onDeleteSettlement = viewModel::onDeleteSettlement,
            onDismiss = viewModel::onDismissSettle,
        )
    }

    val toDelete by viewModel.settlementToDelete.collectAsStateWithLifecycle()
    toDelete?.let { settlement ->
        val text = stringResource(
            R.string.settle_history_row,
            settlementSentence(settlement, loaded),
            shekels(settlement.amountAgorot),
        )
        ConfirmDeleteDialog(
            title = stringResource(R.string.settle_delete_title),
            body = stringResource(R.string.settle_delete_body, text),
            onConfirm = viewModel::onConfirmDeleteSettlement,
            onDismiss = viewModel::onDismissDeleteSettlement,
        )
    }
}

/** Add a traveler (name only), or rename / remove one. Remove is disabled, with the reason, when they're in use. */
@Composable
fun TravelerDialog(
    state: TravelerDialogState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val editing = state.editing
    // Typed text is Compose state (starting from the traveler's name), never read back while typing.
    var name by rememberSaveable { mutableStateOf(state.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editing == null) R.string.traveler_add_title else R.string.traveler_edit_title))
        },
        text = {
            TravelerDialogText(
                name = name,
                onNameChange = {
                    name = it.limitTo(TextLimits.TRAVELER_NAME)
                    onNameChange(name)
                },
                state = state,
                onSave = onSave,
            )
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TravelerDismissButtons(state, onRemove, onDismiss) },
    )
}

/** The name field, and why Remove is disabled when the traveler is in use. */
@Composable
private fun TravelerDialogText(
    name: String,
    onNameChange: (String) -> Unit,
    state: TravelerDialogState,
    onSave: () -> Unit,
) {
    val editing = state.editing
    Column {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.traveler_name)) },
            placeholder = { Text(stringResource(R.string.traveler_name_placeholder)) },
            singleLine = true,
            isError = state.error != null,
            supportingText = errorSupportingText(state.error?.messageRes()),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
        if (editing != null && state.removeBlocked) {
            Text(
                text = stringResource(R.string.traveler_in_use, editing.name),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/** Remove (editing only; greyed out while the traveler is in use) and Cancel. */
@Composable
private fun TravelerDismissButtons(state: TravelerDialogState, onRemove: () -> Unit, onDismiss: () -> Unit) {
    val editing = state.editing
    Row {
        if (editing != null) {
            TextButton(onClick = onRemove, enabled = !state.isSaving && !state.removeBlocked) {
                Text(
                    stringResource(R.string.traveler_remove),
                    color = if (state.removeBlocked) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
        TextButton(onClick = onDismiss, enabled = !state.isSaving) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

private fun TravelerNameError.messageRes(): Int = when (this) {
    TravelerNameError.EMPTY -> R.string.error_traveler_name_empty
    TravelerNameError.DUPLICATE -> R.string.error_traveler_name_duplicate
    TravelerNameError.RESERVED -> R.string.error_traveler_name_reserved
}
