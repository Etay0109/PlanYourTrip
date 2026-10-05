package com.example.planyourtrip.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// Material 3 DatePicker reads and returns dates as UTC midnight millis. Converting through
// the device time zone instead would shift dates by one day west/east of UTC.

fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun pickerMillisToLocalDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
