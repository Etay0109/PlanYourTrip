package com.example.planyourtrip.ui.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.theme.Dimens

/** The delete action of an edit form: the top bar icon's label and the confirmation dialog's texts. */
class FormDelete(
    val iconDescription: String,
    val dialogTitle: String,
    val dialogBody: String,
    val onDelete: () -> Unit,
)

/**
 * The shared shell of the create/edit forms (expense, activity): a back top bar with [title] and, when editing
 * ([delete] not null) and loaded, a delete icon that asks for confirmation; nothing below the bar while [isLoading]
 * (so the fields never flash empty); then the scrolling [fields] and the "Save" button ([saveText]).
 */
@Composable
fun EditFormScaffold(
    title: String,
    isLoading: Boolean,
    isSaving: Boolean,
    saveText: String,
    onBack: () -> Unit,
    onSave: () -> Unit,
    /** Null for a new item (no delete icon). */
    delete: FormDelete?,
    fields: @Composable ColumnScope.() -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackTopAppBar(
                onBack = onBack,
                title = title,
                actions = {
                    if (delete != null && !isLoading) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !isSaving) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = delete.iconDescription)
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (isLoading) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fields()

            Spacer(Modifier.height(16.dp))

            PrimaryButton(
                text = saveText,
                onClick = onSave,
                loading = isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (confirmDelete && delete != null) {
        ConfirmDeleteDialog(
            title = delete.dialogTitle,
            body = delete.dialogBody,
            onConfirm = {
                confirmDelete = false
                delete.onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
