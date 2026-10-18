package com.example.planyourtrip.ui.tripdetails.prep

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.TextLimits
import com.example.planyourtrip.data.trip.limitTo
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.DatePickerPopup
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing

/**
 * "Checklist", the two list chips with their counts, and a card with the selected list: one row per item in the
 * order they were added, then "Add item…". The selected list is Compose state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChecklistSection(state: PrepUiState.Loaded, actions: PrepActions) {
    var selected by rememberSaveable { mutableStateOf(ChecklistKind.BEFORE_TRIP) }
    val list = state.checklist(selected)
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
        PrepSectionTitle(stringResource(R.string.prep_checklist))
        Spacer(Modifier.height(Spacing.m))
        FlowRow(
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sPlus),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ChecklistKind.entries.forEach { kind ->
                val count = state.checklist(kind).count
                ListChip(
                    text = stringResource(R.string.prep_list_chip, stringResource(kind.labelRes()), count.done, count.total),
                    selected = kind == selected,
                    onClick = { selected = kind },
                )
            }
        }
        Spacer(Modifier.height(Spacing.l))
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = Dimens.CardElevation,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding),
        ) {
            Column(Modifier.padding(vertical = Spacing.s)) {
                if (list.rows.isEmpty()) {
                    EmptyList(onAddSuggested = { actions.onAddSuggested(selected) })
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = Spacing.xl),
                    )
                }
                list.rows.forEach { row ->
                    ChecklistRow(
                        row = row,
                        hasDueDates = selected == ChecklistKind.BEFORE_TRIP,
                        actions = actions,
                    )
                }
                AddItemRow(kind = selected, onAdd = actions.onAddItem)
            }
        }
    }
}

private fun ChecklistKind.labelRes(): Int = when (this) {
    ChecklistKind.BEFORE_TRIP -> R.string.prep_list_before_trip
    ChecklistKind.PACKING -> R.string.prep_list_packing
}

/** Selected: filled teal; the other: white with a thin outline (as in the design). */
@Composable
private fun ListChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (selected) null else BorderStroke(Dimens.Hairline, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.heightIn(min = Dimens.TouchTarget),
    ) {
        Box(Modifier.padding(horizontal = Spacing.lPlus, vertical = Spacing.m), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EmptyList(onAddSuggested: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.m)) {
        Text(
            text = stringResource(R.string.prep_list_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.m))
        OutlinedButton(onClick = onAddSuggested, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
            Text(stringResource(R.string.prep_add_suggested), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Tap toggles the item; a long press opens its menu (edit text, due date for "Before the trip", delete). Done items
 * have a filled teal check and gray, struck-through text; an overdue date is shown in the warning color.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistRow(row: ChecklistRowState, hasDueDates: Boolean, actions: PrepActions) {
    var menuOpen by rememberSaveable(row.id) { mutableStateOf(false) }
    var pickingDate by rememberSaveable(row.id) { mutableStateOf(false) }
    val doneLabel = stringResource(if (row.checked) R.string.prep_item_done else R.string.prep_item_not_done)
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    role = Role.Checkbox,
                    onLongClickLabel = stringResource(R.string.prep_item_options),
                    onLongClick = { menuOpen = true },
                    onClick = { actions.onToggle(row.id) },
                )
                .semantics { stateDescription = doneLabel }
                .heightIn(min = RowMinHeight)
                .padding(horizontal = Spacing.xl, vertical = Spacing.sPlus),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckCircle(row.checked)
            Spacer(Modifier.width(Spacing.l))
            Text(
                text = row.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (row.checked) TextDecoration.LineThrough else null,
                ),
                color = if (row.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            row.dueDate?.let { due ->
                Spacer(Modifier.width(Spacing.m))
                val overdue = row.dueState == DueState.OVERDUE
                val date = TripFormatting.dayMonth(due)
                val spoken = stringResource(if (overdue) R.string.prep_item_overdue else R.string.prep_item_due, date)
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overdue) ExtendedTheme.colors.budgetWarning else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.prep_item_edit_text)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                onClick = {
                    menuOpen = false
                    actions.onEditItemText(row.id)
                },
            )
            if (hasDueDates) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (row.dueDate == null) R.string.prep_item_set_due else R.string.prep_item_change_due,
                            ),
                        )
                    },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_event), contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        pickingDate = true
                    },
                )
                if (row.dueDate != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.prep_item_clear_due)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            actions.onSetDueDate(row.id, null)
                        },
                    )
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                onClick = {
                    menuOpen = false
                    actions.onDeleteItem(row.id)
                },
            )
        }
    }
    if (pickingDate) {
        DatePickerPopup(
            initialDate = row.dueDate,
            onDatePicked = { actions.onSetDueDate(row.id, it) },
            onDismiss = { pickingDate = false },
        )
    }
}

/**
 * Checked: filled teal with a check. Unchecked: an empty circle with a clear `onSurfaceVariant` ring (≥ 3:1 on the card; the
 * theme's `outline` is too faint), so it
 * reads as something to tap. The whole row is the touch target (at least 56dp tall).
 */
@Composable
private fun CheckCircle(checked: Boolean) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(Dimens.ChecklistCheckSize)
            .then(
                if (checked) {
                    Modifier.background(MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier.border(Dimens.ChecklistCheckBorder, MaterialTheme.colorScheme.onSurfaceVariant, shape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(IconSize.xs),
            )
        }
    }
}

/**
 * The card's last row: "+" and a borderless field. Done adds the item and keeps the field focused (the keyboard
 * stays open for the next one). The text is Compose state (see the converter cursor note in CLAUDE.md).
 */
@Composable
private fun AddItemRow(kind: ChecklistKind, onAdd: (ChecklistKind, String) -> Boolean) {
    var text by rememberSaveable(kind) { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(horizontal = Spacing.xl, vertical = Spacing.sPlus),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.ChecklistCheckSize),
        )
        Spacer(Modifier.width(Spacing.l))
        val placeholder = stringResource(R.string.prep_add_item)
        BasicTextField(
            value = text,
            onValueChange = { text = it.limitTo(TextLimits.CHECKLIST_TEXT) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            // A custom onDone doesn't hide the keyboard, so the next item can be typed right away.
            keyboardActions = KeyboardActions(onDone = { if (onAdd(kind, text)) text = "" }),
            modifier = Modifier.weight(1f).semantics { contentDescription = placeholder },
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    field()
                }
            },
        )
    }
}

/** "Edit text" of a checklist item. */
@Composable
fun EditItemTextDialog(
    state: EditItemTextState,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Typed text is Compose state (starting from the item's text), never read back while typing.
    var text by rememberSaveable { mutableStateOf(state.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prep_item_edit_text)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it.limitTo(TextLimits.CHECKLIST_TEXT)
                    onTextChange(text)
                },
                label = { Text(stringResource(R.string.prep_item_text)) },
                isError = state.isError,
                supportingText = errorSupportingText(R.string.prep_item_text_empty.takeIf { state.isError }),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A checklist row and the "Add item…" row. */
private val RowMinHeight = 56.dp
