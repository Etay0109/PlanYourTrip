package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.tripdetails.BudgetDialogState
import com.example.planyourtrip.ui.tripdetails.BudgetFieldError

/** Dialog to set or change the trip's total budget (in shekels). */
@Composable
fun BudgetDialog(
    state: BudgetDialogState,
    onInputChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Typed text is Compose state (starting from the current budget), never read back while typing.
    var input by rememberSaveable { mutableStateOf(state.input) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_dialog_title)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    onInputChange(it)
                },
                label = { Text(stringResource(R.string.budget_dialog_label)) },
                prefix = { Text(stringResource(R.string.currency_symbol)) },
                singleLine = true,
                isError = state.error != null,
                supportingText = errorSupportingText(state.error?.messageRes()),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
            )
        },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun BudgetFieldError.messageRes(): Int = when (this) {
    BudgetFieldError.EMPTY -> R.string.error_empty_budget
    BudgetFieldError.INVALID -> R.string.error_invalid_budget
}
