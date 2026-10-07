package com.example.planyourtrip.ui.common.components

import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.common.pickerMillisToLocalDate
import com.example.planyourtrip.ui.common.toPickerMillis
import com.example.planyourtrip.ui.theme.DatePickerHeadlineStyle
import java.time.LocalDate

/**
 * Read-only field that opens a Material 3 date picker when tapped. Days before [minDate] and after
 * [maxDate] are disabled in the picker (the ViewModel still validates).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    @StringRes label: Int,
    date: LocalDate?,
    @StringRes errorRes: Int?,
    initialPickerDate: LocalDate?,
    onDatePicked: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            if (it is PressInteraction.Release) showPicker = true
        }
    }

    OutlinedTextField(
        value = date?.let { TripFormatting.fieldDate(it) }.orEmpty(),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(R.string.new_trip_pick_date)) },
        trailingIcon = { Icon(painterResource(R.drawable.ic_event), contentDescription = null) },
        isError = errorRes != null,
        supportingText = errorSupportingText(errorRes),
        singleLine = true,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    )

    if (showPicker) {
        DatePickerPopup(
            initialDate = initialPickerDate,
            onDatePicked = onDatePicked,
            onDismiss = { showPicker = false },
            minDate = minDate,
            maxDate = maxDate,
        )
    }
}

/**
 * The Material 3 date picker in a dialog, without a field (also used by the Prep checklist's due dates). Days
 * before [minDate] and after [maxDate] are disabled. Picker millis are UTC midnight, converted with
 * `toPickerMillis()` / `pickerMillisToLocalDate()`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerPopup(
    initialDate: LocalDate?,
    onDatePicked: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    val minMillis = minDate?.toPickerMillis()
    val maxMillis = maxDate?.toPickerMillis()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.toPickerMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                (minMillis == null || utcTimeMillis >= minMillis) &&
                    (maxMillis == null || utcTimeMillis <= maxMillis)

            override fun isSelectableYear(year: Int) =
                (minDate == null || year >= minDate.year) && (maxDate == null || year <= maxDate.year)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { onDatePicked(pickerMillisToLocalDate(it)) }
                    onDismiss()
                },
                enabled = pickerState.selectedDateMillis != null,
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        // The picker's "Selected date" headline uses headlineLarge; give it a calmer style.
        MaterialTheme(typography = MaterialTheme.typography.copy(headlineLarge = DatePickerHeadlineStyle)) {
            DatePicker(state = pickerState)
        }
    }
}
