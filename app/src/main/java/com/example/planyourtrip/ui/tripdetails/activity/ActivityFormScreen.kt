package com.example.planyourtrip.ui.tripdetails.activity

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.TextLimits
import com.example.planyourtrip.data.trip.limitTo
import com.example.planyourtrip.ui.common.components.DateField
import com.example.planyourtrip.ui.common.components.EditFormScaffold
import com.example.planyourtrip.ui.common.components.FormDelete
import com.example.planyourtrip.ui.common.components.TimeField
import com.example.planyourtrip.ui.common.components.TintedFilterChip
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.common.iconRes
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun ActivityFormScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ActivityFormViewModel = viewModel(factory = ActivityFormViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }
    ActivityFormContent(
        state = state,
        actions = remember(viewModel) {
            ActivityFormActions(
                onTitleChange = viewModel::onTitleChange,
                onTypeChange = viewModel::onTypeChange,
                onDateChange = viewModel::onDateChange,
                onStartTimeChange = viewModel::onStartTimeChange,
                onLocationChange = viewModel::onLocationChange,
                onCostChange = viewModel::onCostChange,
                onBookedChange = viewModel::onBookedChange,
                onNoteChange = viewModel::onNoteChange,
                onSave = viewModel::onSave,
                onDelete = viewModel::onDelete,
            )
        },
        onBack = onBack,
    )
}

/** Callbacks of [ActivityFormContent], grouped so the screen passes one value. */
class ActivityFormActions(
    val onTitleChange: (String) -> Unit,
    val onTypeChange: (ActivityType) -> Unit,
    val onDateChange: (LocalDate) -> Unit,
    /** Null clears the time. */
    val onStartTimeChange: (LocalTime?) -> Unit,
    val onLocationChange: (String) -> Unit,
    val onCostChange: (String) -> Unit,
    val onBookedChange: (Boolean) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActivityFormContent(
    state: ActivityFormUiState,
    actions: ActivityFormActions,
    onBack: () -> Unit,
) {
    EditFormScaffold(
        title = stringResource(if (state.isEditing) R.string.activity_edit_title else R.string.activity_new_title),
        isLoading = state.isLoading,
        isSaving = state.isSaving,
        saveText = stringResource(R.string.activity_save),
        onBack = onBack,
        onSave = actions.onSave,
        delete = if (state.isEditing) {
            FormDelete(
                iconDescription = stringResource(R.string.activity_delete),
                dialogTitle = stringResource(R.string.activity_delete_title),
                dialogBody = stringResource(R.string.activity_delete_body, state.title.trim()),
                onDelete = actions.onDelete,
            )
        } else {
            null
        },
    ) {
        // Typed text is Compose state (starting from the loaded or restored value), never read back while typing.
        var title by rememberSaveable { mutableStateOf(state.title) }
        var location by rememberSaveable { mutableStateOf(state.location) }
        var cost by rememberSaveable { mutableStateOf(state.cost) }
        var note by rememberSaveable { mutableStateOf(state.note) }
        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it.limitTo(TextLimits.ACTIVITY_TITLE)
                actions.onTitleChange(title)
            },
            label = { Text(stringResource(R.string.activity_title)) },
            placeholder = { Text(stringResource(R.string.activity_title_placeholder)) },
            singleLine = true,
            isError = state.titleError != null,
            supportingText = errorSupportingText(state.titleError?.messageRes()),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.activity_type),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ActivityType.entries.forEach { type ->
                // Each chip shows its type color: the icon always, the tinted fill when selected.
                TintedFilterChip(
                    selected = type == state.type,
                    onClick = { actions.onTypeChange(type) },
                    label = stringResource(type.labelRes()),
                    icon = type.iconRes(),
                    color = ExtendedTheme.colors.activityType(type),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xs))

        DateField(
            label = R.string.activity_date,
            date = state.date,
            errorRes = null,
            initialPickerDate = state.date,
            onDatePicked = actions.onDateChange,
            minDate = state.tripStart,
            maxDate = state.tripEnd,
        )

        TimeField(time = state.startTime, onTimeChange = actions.onStartTimeChange)

        OutlinedTextField(
            value = location,
            onValueChange = {
                location = it.limitTo(TextLimits.ACTIVITY_LOCATION)
                actions.onLocationChange(location)
            },
            label = { Text(stringResource(R.string.activity_location)) },
            placeholder = { Text(stringResource(R.string.activity_location_placeholder)) },
            singleLine = true,
            leadingIcon = { Icon(painterResource(R.drawable.ic_location), contentDescription = null) },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = cost,
            onValueChange = {
                cost = it
                actions.onCostChange(it)
            },
            label = { Text(stringResource(R.string.activity_cost)) },
            prefix = { Text(stringResource(R.string.currency_symbol)) },
            singleLine = true,
            isError = state.costError != null,
            supportingText = errorSupportingText(state.costError?.messageRes()),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )

        BookedRow(booked = state.booked, onBookedChange = actions.onBookedChange)

        OutlinedTextField(
            value = note,
            onValueChange = {
                note = it.limitTo(TextLimits.ACTIVITY_NOTE)
                actions.onNoteChange(note)
            },
            label = { Text(stringResource(R.string.activity_note)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BookedRow(booked: Boolean, onBookedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = booked, role = Role.Switch, onValueChange = onBookedChange)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.activity_booked),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.activity_booked_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.m))
        // The whole row toggles; the switch only shows the value.
        Switch(checked = booked, onCheckedChange = null)
    }
}

@StringRes
private fun ActivityFieldError.messageRes(): Int = when (this) {
    ActivityFieldError.EMPTY_TITLE -> R.string.error_empty_activity_title
    ActivityFieldError.INVALID_COST -> R.string.error_invalid_cost
}

private val previewActions = ActivityFormActions(
    onTitleChange = {}, onTypeChange = {}, onDateChange = {}, onStartTimeChange = {}, onLocationChange = {},
    onCostChange = {}, onBookedChange = {}, onNoteChange = {}, onSave = {}, onDelete = {},
)

@Preview(showBackground = true, heightDp = 1100)
@Composable
internal fun ActivityFormPreview() {
    PlanYourTripTheme(darkTheme = false) {
        ActivityFormContent(
            state = ActivityFormUiState(
                isEditing = true,
                isLoading = false,
                title = "Colosseum",
                type = ActivityType.ACTIVITY,
                date = LocalDate.of(2026, 11, 12),
                startTime = LocalTime.of(10, 0),
                location = "Piazza del Colosseo, 1",
                cost = "0",
                booked = true,
                note = "Skip-the-line tour booked",
                costError = ActivityFieldError.INVALID_COST,
            ),
            actions = previewActions,
            onBack = {},
        )
    }
}

@Preview(name = "Activity form – narrow, large font", widthDp = 320, heightDp = 1400, fontScale = 1.5f)
@Composable
internal fun ActivityFormNarrowPreview() {
    PlanYourTripTheme(darkTheme = false) {
        ActivityFormContent(
            state = ActivityFormUiState(isLoading = false, date = LocalDate.of(2026, 11, 12)),
            actions = previewActions,
            onBack = {},
        )
    }
}

@Preview(name = "Activity form – dining selected, dark", heightDp = 1100)
@Composable
internal fun ActivityFormDarkPreview() {
    PlanYourTripTheme(darkTheme = true) {
        ActivityFormContent(
            state = ActivityFormUiState(
                isLoading = false,
                title = "Dinner in Trastevere",
                type = ActivityType.DINING,
                date = LocalDate.of(2026, 11, 12),
                startTime = LocalTime.of(20, 0),
                booked = true,
            ),
            actions = previewActions,
            onBack = {},
        )
    }
}
