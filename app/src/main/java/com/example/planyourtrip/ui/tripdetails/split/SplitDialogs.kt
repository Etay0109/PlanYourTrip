package com.example.planyourtrip.ui.tripdetails.split

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.TextLimits
import com.example.planyourtrip.data.trip.limitTo
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.ConfirmDeleteDialog
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.common.split.Transfer
import com.example.planyourtrip.ui.common.split.TravelerNameError
import com.example.planyourtrip.ui.theme.Dimens
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
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.limitTo(TextLimits.TRAVELER_NAME)
                        onNameChange(name)
                    },
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
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
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
        },
    )
}

private fun TravelerNameError.messageRes(): Int = when (this) {
    TravelerNameError.EMPTY -> R.string.error_traveler_name_empty
    TravelerNameError.DUPLICATE -> R.string.error_traveler_name_duplicate
    TravelerNameError.RESERVED -> R.string.error_traveler_name_reserved
}

/**
 * "Settle up": pick an open debt, adjust the amount for a partial payment, "Mark as paid". "History" lists the
 * recorded payments, each deletable (the debt reopens).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpSheet(
    sheet: SettleSheetState,
    state: SplitUiState.Loaded,
    onSelect: (Transfer) -> Unit,
    onAmountChange: (String) -> Unit,
    onMarkAsPaid: () -> Unit,
    onShowHistory: () -> Unit,
    onShowTransfers: () -> Unit,
    onDeleteSettlement: (Settlement) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding)
                .padding(bottom = Spacing.xxl),
        ) {
            when (sheet.page) {
                SettlePage.TRANSFERS -> TransfersPage(sheet, state, onSelect, onAmountChange, onMarkAsPaid, onShowHistory)
                SettlePage.HISTORY -> HistoryPage(state, onDeleteSettlement, onShowTransfers)
            }
        }
    }
}

@Composable
private fun TransfersPage(
    sheet: SettleSheetState,
    state: SplitUiState.Loaded,
    onSelect: (Transfer) -> Unit,
    onAmountChange: (String) -> Unit,
    onMarkAsPaid: () -> Unit,
    onShowHistory: () -> Unit,
) {
    SheetTitle(R.string.settle_up, actionLabel = R.string.settle_history, onAction = onShowHistory)
    if (state.transfers.isEmpty()) {
        Text(
            text = stringResource(R.string.settle_nothing_open),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Spacing.l),
        )
        return
    }
    Text(
        text = stringResource(R.string.settle_pick),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.m))
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        state.transfers.forEach { transfer ->
            val selected = transfer == sheet.selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(transfer) })
                    .padding(horizontal = Spacing.s, vertical = Spacing.s),
            ) {
                RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = Spacing.s))
                TransferAvatars(state.person(transfer.fromKey), state.person(transfer.toKey))
                Spacer(Modifier.width(Spacing.m))
                Text(
                    text = transferSentence(state.person(transfer.fromKey), state.person(transfer.toKey)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.s))
                Text(
                    text = shekels(transfer.amountAgorot),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(end = Spacing.s),
                )
            }
        }
    }
    val selected = sheet.selected ?: return
    // Typed text is Compose state; picking another transfer starts it again at that transfer's full amount.
    var amount by rememberSaveable(selected) { mutableStateOf(sheet.amount) }
    Spacer(Modifier.height(Spacing.l))
    OutlinedTextField(
        value = amount,
        onValueChange = {
            amount = it
            onAmountChange(it)
        },
        label = { Text(stringResource(R.string.settle_amount)) },
        prefix = { Text(stringResource(R.string.currency_symbol)) },
        singleLine = true,
        isError = sheet.error != null,
        supportingText = {
            Text(
                when (sheet.error) {
                    null -> stringResource(R.string.settle_amount_hint)
                    SettleAmountError.EMPTY -> stringResource(R.string.error_settle_empty)
                    SettleAmountError.INVALID -> stringResource(R.string.error_settle_invalid)
                    SettleAmountError.TOO_MUCH ->
                        stringResource(R.string.error_settle_too_much, shekels(selected.amountAgorot))
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onMarkAsPaid() }),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(Spacing.m))
    PrimaryButton(
        text = stringResource(R.string.settle_mark_paid),
        onClick = onMarkAsPaid,
        loading = sheet.isSaving,
        leadingIcon = R.drawable.ic_check,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun HistoryPage(
    state: SplitUiState.Loaded,
    onDeleteSettlement: (Settlement) -> Unit,
    onShowTransfers: () -> Unit,
) {
    SheetTitle(R.string.settle_history, actionLabel = R.string.settle_back, onAction = onShowTransfers)
    if (state.settlements.isEmpty()) {
        Text(
            text = stringResource(R.string.settle_history_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = Spacing.l),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        state.settlements.forEach { settlement ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large)
                    .padding(start = Spacing.l, top = Spacing.xs, bottom = Spacing.xs),
            ) {
                TransferAvatars(state.person(settlement.fromKey), state.person(settlement.toKey))
                Spacer(Modifier.width(Spacing.m))
                // Like the expense rows: who paid whom with the date under it, the amount in bold on the right.
                Column(Modifier.weight(1f).padding(vertical = Spacing.s)) {
                    Text(
                        text = settlementSentence(settlement, state),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = TripFormatting.date(settlement.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(Spacing.s))
                Text(
                    text = shekels(settlement.amountAgorot),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
                IconButton(onClick = { onDeleteSettlement(settlement) }) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.settle_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Sheet title with a text link on the right ("History" / "Open debts"). */
@Composable
private fun SheetTitle(title: Int, actionLabel: Int, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        TextButton(onClick = onAction) { Text(stringResource(actionLabel)) }
    }
    Spacer(Modifier.height(Spacing.s))
}
