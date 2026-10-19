package com.example.planyourtrip.ui.tripdetails.prep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.planyourtrip.R
import com.example.planyourtrip.data.document.DocumentType
import com.example.planyourtrip.ui.common.components.ConfirmDeleteDialog
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing

/** New document (after the file was picked) or edit: name, type chips and the optional detail line. */
@Composable
fun DocumentFormDialog(
    state: DocumentFormState,
    onNameChange: (String) -> Unit,
    onTypeChange: (DocumentType) -> Unit,
    onDetailChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Typed text is Compose state (starting from the file's or document's name), never read back while typing.
    var name by rememberSaveable { mutableStateOf(state.name) }
    var detail by rememberSaveable { mutableStateOf(state.detail) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (state.isNew) R.string.prep_document_new_title else R.string.prep_document_edit_title,
                ),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        onNameChange(it)
                    },
                    label = { Text(stringResource(R.string.prep_document_name)) },
                    isError = state.nameError,
                    supportingText = errorSupportingText(R.string.prep_document_name_empty.takeIf { state.nameError }),
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.m))
                DocumentTypeChips(selected = state.type, enabled = !state.isSaving, onTypeChange = onTypeChange)
                Spacer(Modifier.height(Spacing.m))
                OutlinedTextField(
                    value = detail,
                    onValueChange = {
                        detail = it
                        onDetailChange(it)
                    },
                    label = { Text(stringResource(R.string.prep_document_detail)) },
                    placeholder = { Text(stringResource(R.string.prep_document_detail_hint)) },
                    singleLine = true,
                    enabled = !state.isSaving,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { SaveButton(isSaving = state.isSaving, onSave = onSave) },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** "Type" and one chip per document type. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentTypeChips(selected: DocumentType, enabled: Boolean, onTypeChange: (DocumentType) -> Unit) {
    Text(
        text = stringResource(R.string.prep_document_type),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.xs))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        DocumentType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onTypeChange(type) },
                enabled = enabled,
                label = { Text(stringResource(type.labelRes())) },
                leadingIcon = {
                    Icon(
                        painterResource(type.iconRes()),
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.s),
                    )
                },
            )
        }
    }
}

/** "Save", or a small spinner while saving. */
@Composable
private fun SaveButton(isSaving: Boolean, onSave: () -> Unit) {
    TextButton(onClick = onSave, enabled = !isSaving) {
        if (isSaving) {
            CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = Dimens.ProgressStroke)
        } else {
            Text(stringResource(R.string.action_save))
        }
    }
}

/** "Delete this document?" — the row and its file go. */
@Composable
fun DeleteDocumentDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDeleteDialog(
        title = stringResource(R.string.prep_delete_document_title),
        body = stringResource(R.string.prep_delete_document_body, name),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
