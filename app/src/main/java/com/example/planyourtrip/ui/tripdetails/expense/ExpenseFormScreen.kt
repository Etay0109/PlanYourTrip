package com.example.planyourtrip.ui.tripdetails.expense

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.TextLimits
import com.example.planyourtrip.data.trip.limitTo
import com.example.planyourtrip.ui.common.components.DateField
import com.example.planyourtrip.ui.common.components.EditFormScaffold
import com.example.planyourtrip.ui.common.components.FormDelete
import com.example.planyourtrip.ui.common.components.TintedFilterChip
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.common.iconRes
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.Spacing
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
        actions = remember(viewModel) {
            ExpenseFormActions(
                onNameChange = viewModel::onNameChange,
                onAmountChange = viewModel::onAmountChange,
                onCurrencyChange = viewModel::onCurrencyChange,
                onCategoryChange = viewModel::onCategoryChange,
                onDateChange = viewModel::onDateChange,
                onPaidByChange = viewModel::onPaidByChange,
                onSplitToggle = viewModel::onSplitToggle,
                onNotShared = viewModel::onNotShared,
                onSave = viewModel::onSave,
                onDelete = viewModel::onDelete,
            )
        },
        onBack = onBack,
    )
}

/** Callbacks of [ExpenseFormContent], grouped so the screen passes one value. */
class ExpenseFormActions(
    val onNameChange: (String) -> Unit,
    val onAmountChange: (String) -> Unit,
    /** ILS or the foreign currency code. */
    val onCurrencyChange: (String) -> Unit,
    val onCategoryChange: (ExpenseCategory) -> Unit,
    val onDateChange: (LocalDate) -> Unit,
    val onPaidByChange: (String) -> Unit,
    val onSplitToggle: (String) -> Unit,
    val onNotShared: () -> Unit,
    val onSave: () -> Unit,
    val onDelete: () -> Unit,
)

@Composable
fun ExpenseFormContent(
    state: ExpenseFormUiState,
    actions: ExpenseFormActions,
    onBack: () -> Unit,
) {
    EditFormScaffold(
        title = stringResource(if (state.isEditing) R.string.expense_edit_title else R.string.expense_new_title),
        isLoading = state.isLoading,
        isSaving = state.isSaving,
        saveText = stringResource(R.string.expense_save),
        onBack = onBack,
        onSave = actions.onSave,
        delete = if (state.isEditing) {
            FormDelete(
                iconDescription = stringResource(R.string.expense_delete),
                dialogTitle = stringResource(R.string.expense_delete_title),
                dialogBody = stringResource(R.string.expense_delete_body, state.name.trim()),
                onDelete = actions.onDelete,
            )
        } else {
            null
        },
    ) {
        // Typed text is Compose state (starting from the loaded or restored value), never read back while typing.
        var name by rememberSaveable { mutableStateOf(state.name) }
        var amount by rememberSaveable { mutableStateOf(state.amount) }
        ExpenseNameField(
            name = name,
            onNameChange = {
                name = it.limitTo(TextLimits.EXPENSE_NAME)
                actions.onNameChange(name)
            },
            error = state.nameError,
        )

        AmountField(
            state = state,
            amount = amount,
            onAmountChange = {
                amount = it
                actions.onAmountChange(it)
            },
            onCurrencyChange = actions.onCurrencyChange,
        )

        if (state.showSplit) {
            SplitFields(state, actions.onPaidByChange, actions.onSplitToggle, actions.onNotShared)
        }

        DateField(
            label = R.string.expense_date,
            date = state.date,
            errorRes = state.dateError?.dateMessageRes(),
            // A date already after the trip opens the picker on the last selectable day.
            initialPickerDate = state.tripEnd?.let { minOf(state.date, it) } ?: state.date,
            onDatePicked = actions.onDateChange,
            maxDate = state.tripEnd,
        )

        CategoryChips(selected = state.category, onCategoryChange = actions.onCategoryChange)
    }
}

@Composable
private fun ExpenseNameField(name: String, onNameChange: (String) -> Unit, error: ExpenseFieldError?) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.expense_name)) },
        placeholder = { Text(stringResource(R.string.expense_name_placeholder)) },
        singleLine = true,
        isError = error != null,
        supportingText = errorSupportingText(error?.nameMessageRes()),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next,
        ),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "Category" and one chip per category. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(selected: ExpenseCategory, onCategoryChange: (ExpenseCategory) -> Unit) {
    Text(
        text = stringResource(R.string.expense_category),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ExpenseCategory.entries.forEach { category ->
            // Each chip shows its category color: the icon always, the tinted fill when selected.
            TintedFilterChip(
                selected = category == selected,
                onClick = { onCategoryChange(category) },
                label = stringResource(category.labelRes()),
                icon = category.iconRes(),
                color = ExtendedTheme.colors.category(category),
            )
        }
    }
}

/** The name field's only error. */
@StringRes
private fun ExpenseFieldError.nameMessageRes(): Int = R.string.error_empty_expense_name

/** The date field's only error. */
@StringRes
private fun ExpenseFieldError.dateMessageRes(): Int = R.string.error_expense_after_trip
