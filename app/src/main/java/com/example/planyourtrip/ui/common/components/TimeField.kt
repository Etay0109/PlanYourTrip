package com.example.planyourtrip.ui.common.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.TripFormatting
import java.time.LocalTime

/** Read-only "Start time" field: tapping opens a 24-hour Material 3 time picker; × clears the time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(time: LocalTime?, onTimeChange: (LocalTime?) -> Unit, modifier: Modifier = Modifier) {
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
        modifier = modifier.fillMaxWidth(),
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
