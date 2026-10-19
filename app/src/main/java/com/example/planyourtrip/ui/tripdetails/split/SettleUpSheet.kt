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
import androidx.compose.ui.text.input.KeyboardType
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.common.split.Transfer
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.Spacing

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
            TransferChoice(transfer, selected = transfer == sheet.selected, state = state, onSelect = onSelect)
        }
    }
    val selected = sheet.selected ?: return
    Spacer(Modifier.height(Spacing.l))
    SettleAmountField(selected, sheet, onAmountChange, onMarkAsPaid)
    Spacer(Modifier.height(Spacing.m))
    PrimaryButton(
        text = stringResource(R.string.settle_mark_paid),
        onClick = onMarkAsPaid,
        loading = sheet.isSaving,
        leadingIcon = R.drawable.ic_check,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** One open debt as a radio row: avatars, "Noa owes you", the amount. */
@Composable
private fun TransferChoice(
    transfer: Transfer,
    selected: Boolean,
    state: SplitUiState.Loaded,
    onSelect: (Transfer) -> Unit,
) {
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

/** The amount paid for [selected] (its full amount to start with; less for a partial payment). */
@Composable
private fun SettleAmountField(
    selected: Transfer,
    sheet: SettleSheetState,
    onAmountChange: (String) -> Unit,
    onMarkAsPaid: () -> Unit,
) {
    // Typed text is Compose state; picking another transfer starts it again at that transfer's full amount.
    var amount by rememberSaveable(selected) { mutableStateOf(sheet.amount) }
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
