package com.example.planyourtrip.ui.tripdetails.expense

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.ui.common.components.BackTopAppBar
import com.example.planyourtrip.ui.common.components.DateField
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.tripdetails.iconRes
import com.example.planyourtrip.ui.tripdetails.labelRes
import java.time.LocalDate

@Composable
fun ExpenseFormScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: ExpenseFormViewModel = viewModel(factory = ExpenseFormViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }
    ExpenseFormContent(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onAmountChange = viewModel::onAmountChange,
        onCategoryChange = viewModel::onCategoryChange,
        onDateChange = viewModel::onDateChange,
        onSave = viewModel::onSave,
        onDelete = viewModel::onDelete,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseFormContent(
    state: ExpenseFormUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onCategoryChange: (ExpenseCategory) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackTopAppBar(
                onBack = onBack,
                title = stringResource(if (state.isEditing) R.string.expense_edit_title else R.string.expense_new_title),
                actions = {
                    if (state.isEditing && !state.isLoading) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !state.isSaving) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.expense_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // Editing: wait for the expense's values so the fields never flash empty.
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
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.expense_name)) },
                placeholder = { Text(stringResource(R.string.expense_name_placeholder)) },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = errorSupportingText(state.nameError?.messageRes()),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                label = { Text(stringResource(R.string.expense_amount)) },
                prefix = { Text(stringResource(R.string.currency_symbol)) },
                singleLine = true,
                isError = state.amountError != null,
                supportingText = errorSupportingText(state.amountError?.messageRes()),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(
                label = R.string.expense_date,
                date = state.date,
                errorRes = null,
                initialPickerDate = state.date,
                onDatePicked = onDateChange,
            )

            Text(
                text = stringResource(R.string.expense_category),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ExpenseCategory.entries.forEach { category ->
                    FilterChip(
                        selected = category == state.category,
                        onClick = { onCategoryChange(category) },
                        label = { Text(stringResource(category.labelRes())) },
                        leadingIcon = {
                            Icon(
                                painterResource(category.iconRes()),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            PrimaryButton(
                text = stringResource(R.string.expense_save),
                onClick = onSave,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.expense_delete_title)) },
            text = { Text(stringResource(R.string.expense_delete_body, state.name.trim())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@StringRes
private fun ExpenseFieldError.messageRes(): Int = when (this) {
    ExpenseFieldError.EMPTY_NAME -> R.string.error_empty_expense_name
    ExpenseFieldError.EMPTY_AMOUNT -> R.string.error_empty_amount
    ExpenseFieldError.INVALID_AMOUNT -> R.string.error_invalid_amount
}

@Preview(showBackground = true)
@Composable
private fun ExpenseFormPreview() {
    PlanYourTripTheme(darkTheme = false) {
        ExpenseFormContent(
            state = ExpenseFormUiState(
                isEditing = true,
                name = "Colosseum guided tour",
                amount = "0",
                category = ExpenseCategory.ATTRACTIONS,
                date = LocalDate.of(2026, 11, 13),
                amountError = ExpenseFieldError.INVALID_AMOUNT,
            ),
            onBack = {}, onNameChange = {}, onAmountChange = {}, onCategoryChange = {}, onDateChange = {},
            onSave = {}, onDelete = {},
        )
    }
}

@Preview(name = "Expense form – narrow, large font", widthDp = 320, heightDp = 900, fontScale = 1.5f)
@Composable
private fun ExpenseFormNarrowPreview() {
    PlanYourTripTheme(darkTheme = false) {
        ExpenseFormContent(
            state = ExpenseFormUiState(date = LocalDate.of(2026, 11, 13)),
            onBack = {}, onNameChange = {}, onAmountChange = {}, onCategoryChange = {}, onDateChange = {},
            onSave = {}, onDelete = {},
        )
    }
}
