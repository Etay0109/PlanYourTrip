package com.example.planyourtrip.ui.tripdetails.prep

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.DatePickerPopup
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing
import java.time.LocalDate

/**
 * Tap toggles the item; a long press opens its menu (edit text, due date for "Before the trip", delete). Done items
 * have a filled teal check and gray, struck-through text; an overdue date is shown in the warning color.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChecklistRow(row: ChecklistRowState, hasDueDates: Boolean, actions: PrepActions) {
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
                .heightIn(min = ChecklistRowMinHeight)
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
                DueDateLabel(due, overdue = row.dueState == DueState.OVERDUE)
            }
        }
        ChecklistItemMenu(
            expanded = menuOpen,
            onClose = { menuOpen = false },
            row = row,
            hasDueDates = hasDueDates,
            actions = actions,
            onPickDate = { pickingDate = true },
        )
    }
    if (pickingDate) {
        DatePickerPopup(
            initialDate = row.dueDate,
            onDatePicked = { actions.onSetDueDate(row.id, it) },
            onDismiss = { pickingDate = false },
        )
    }
}

/** The item's due date ("12 Oct"); an overdue one in the warning color. TalkBack says "due" or "overdue". */
@Composable
private fun DueDateLabel(due: LocalDate, overdue: Boolean) {
        val date = TripFormatting.dayMonth(due)
    val spoken = stringResource(if (overdue) R.string.prep_item_overdue else R.string.prep_item_due, date)
    Text(
        text = date,
        style = MaterialTheme.typography.labelMedium,
        color = if (overdue) ExtendedTheme.colors.budgetWarning else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = spoken },
    )
}

/** Long press: edit the text, set/change/clear the due date ("Before the trip" only), delete. */
@Composable
private fun ChecklistItemMenu(
    expanded: Boolean,
    onClose: () -> Unit,
    row: ChecklistRowState,
    hasDueDates: Boolean,
    actions: PrepActions,
    onPickDate: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onClose) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.prep_item_edit_text)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
            onClick = {
                onClose()
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
                    onClose()
                    onPickDate()
                },
            )
            if (row.dueDate != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.prep_item_clear_due)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_close), contentDescription = null) },
                    onClick = {
                        onClose()
                        actions.onSetDueDate(row.id, null)
                    },
                )
            }
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            onClick = {
                onClose()
                actions.onDeleteItem(row.id)
            },
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
