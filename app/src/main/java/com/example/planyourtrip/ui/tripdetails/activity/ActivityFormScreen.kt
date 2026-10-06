package com.example.planyourtrip.ui.tripdetails.activity

import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.BackTopAppBar
import com.example.planyourtrip.ui.common.components.DateField
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.common.iconRes
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
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
fun ActivityFormContent(state: ActivityFormUiState, actions: ActivityFormActions, onBack: () -> Unit) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackTopAppBar(
                onBack = onBack,
                title = stringResource(if (state.isEditing) R.string.activity_edit_title else R.string.activity_new_title),
                actions = {
                    if (state.isEditing && !state.isLoading) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !state.isSaving) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.activity_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // Wait for the trip dates (and the activity when editing) so the fields never flash empty.
        if (state.isLoading) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = actions.onTitleChange,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ActivityType.entries.forEach { type ->
                    // Each chip shows its type color: the icon always, the tinted fill when selected.
                    val typeColor = ExtendedTheme.colors.activityType(type)
                    FilterChip(
                        selected = type == state.type,
                        onClick = { actions.onTypeChange(type) },
                        label = { Text(stringResource(type.labelRes())) },
                        leadingIcon = {
                            Icon(
                                painterResource(type.iconRes()),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            iconColor = typeColor.content,
                            selectedContainerColor = typeColor.container,
                            selectedLabelColor = typeColor.content,
                            selectedLeadingIconColor = typeColor.content,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = type == state.type,
                            selectedBorderColor = typeColor.content,
                            selectedBorderWidth = 1.dp,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

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
                value = state.location,
                onValueChange = actions.onLocationChange,
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
                value = state.cost,
                onValueChange = actions.onCostChange,
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
                value = state.note,
                onValueChange = actions.onNoteChange,
                label = { Text(stringResource(R.string.activity_note)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            PrimaryButton(
                text = stringResource(R.string.activity_save),
                onClick = actions.onSave,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.activity_delete_title)) },
            text = { Text(stringResource(R.string.activity_delete_body, state.title.trim())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        actions.onDelete()
                    },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Read-only "Start time" field: tapping opens a 24-hour Material 3 time picker; × clears the time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(time: LocalTime?, onTimeChange: (LocalTime?) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            if (it is PressInteraction.Release) showPicker = true
        }
    }

    OutlinedTextField(
        value = time?.let { TripFormatting.time(it) }.orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.activity_time)) },
        placeholder = { Text(stringResource(R.string.activity_time_placeholder)) },
        trailingIcon = {
            if (time != null) {
                IconButton(onClick = { onTimeChange(null) }) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.activity_time_clear),
                    )
                }
            } else {
                Icon(painterResource(R.drawable.ic_schedule), contentDescription = null)
            }
        },
        singleLine = true,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )

    if (showPicker) {
        // Material 3 1.3 has no TimePickerDialog yet, so the picker sits in an AlertDialog.
        val pickerState = rememberTimePickerState(
            initialHour = time?.hour ?: 9,
            initialMinute = time?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.activity_time_dialog_title)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChange(LocalTime.of(pickerState.hour, pickerState.minute))
                        showPicker = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun BookedRow(booked: Boolean, onBookedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = booked, role = Role.Switch, onValueChange = onBookedChange)
            .padding(vertical = 8.dp),
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
        Spacer(Modifier.width(12.dp))
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
private fun ActivityFormPreview() {
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
private fun ActivityFormNarrowPreview() {
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
private fun ActivityFormDarkPreview() {
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
