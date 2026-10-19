package com.example.planyourtrip.ui.tripdetails.prep

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.TextLimits
import com.example.planyourtrip.data.trip.limitTo
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.theme.Dimens
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
 * The card's last row: "+" and a borderless field. Done adds the item and keeps the field focused (the keyboard
 * stays open for the next one). The text is Compose state (see the converter cursor note in CLAUDE.md).
 */
@Composable
private fun AddItemRow(kind: ChecklistKind, onAdd: (ChecklistKind, String) -> Boolean) {
    var text by rememberSaveable(kind) { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ChecklistRowMinHeight)
            .padding(horizontal = Spacing.xl, vertical = Spacing.sPlus),
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
internal val ChecklistRowMinHeight = 56.dp
