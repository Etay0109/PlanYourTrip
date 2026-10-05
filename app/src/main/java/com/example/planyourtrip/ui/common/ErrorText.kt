package com.example.planyourtrip.ui.common

import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * `supportingText` slot for a text field: shows the error message under the field,
 * or null (no slot) when [messageRes] is null.
 */
fun errorSupportingText(@StringRes messageRes: Int?): (@Composable () -> Unit)? =
    messageRes?.let { { Text(stringResource(it)) } }
