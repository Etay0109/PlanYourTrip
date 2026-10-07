package com.example.planyourtrip.ui.tripdetails.prep

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.planyourtrip.R
import com.example.planyourtrip.data.document.DOCUMENT_MIME_TYPES
import com.example.planyourtrip.data.trip.ChecklistKind

/**
 * Everything of the Prep tab that lives outside its list: the system file picker, the file viewer, the dialogs and
 * the snackbars ("Item deleted" + Undo, file messages) on the trip screen's [snackbarHostState]. Returns the
 * actions the tab's content calls.
 */
@Composable
fun rememberPrepActions(viewModel: PrepViewModel, snackbarHostState: SnackbarHostState): PrepActions {
    val context = LocalContext.current

    // Picker for PDFs and images; the picked uri is readable for as long as the app runs, enough for the copy.
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onFilePicked(it.toString()) }
    }

    // "Item deleted" with "Undo", once per delete.
    val deletedItem by viewModel.deletedItem.collectAsStateWithLifecycle()
    val deletedMessage = stringResource(R.string.prep_item_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(deletedItem) {
        val deleted = deletedItem ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.onUndoDeleteItem(deleted)
        } else {
            viewModel.onDeleteItemMessageDismissed(deleted)
        }
    }

    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageText = message?.let { stringResource(it.messageRes()) }
    LaunchedEffect(message) {
        if (messageText == null) return@LaunchedEffect
        viewModel.onMessageShown()
        snackbarHostState.showSnackbar(messageText)
    }

    // Opens a stored file in the system viewer, with read access to that one file only.
    val pendingView by viewModel.pendingView.collectAsStateWithLifecycle()
    val noViewer = stringResource(R.string.prep_no_viewer)
    LaunchedEffect(pendingView) {
        val file = pendingView ?: return@LaunchedEffect
        viewModel.onViewLaunched()
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(file.uri), file.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            snackbarHostState.showSnackbar(noViewer)
        }
    }

    val beforeTripSuggestions = stringArrayResource(R.array.prep_suggested_before_trip).toList()
    val packingSuggestions = stringArrayResource(R.array.prep_suggested_packing).toList()
    return remember(viewModel, pickFile, beforeTripSuggestions, packingSuggestions) {
        PrepActions(
            onAddItem = viewModel::onAddItem,
            onToggle = viewModel::onToggle,
            onEditItemText = viewModel::onEditItemText,
            onSetDueDate = viewModel::onSetDueDate,
            onDeleteItem = viewModel::onDeleteItem,
            onAddSuggested = { kind ->
                val texts = when (kind) {
                    ChecklistKind.BEFORE_TRIP -> beforeTripSuggestions
                    ChecklistKind.PACKING -> packingSuggestions
                }
                viewModel.onAddSuggested(kind, texts)
            },
            onAddDocument = { pickFile.launch(DOCUMENT_MIME_TYPES) },
            onViewDocument = viewModel::onViewDocument,
            onEditDocument = viewModel::onEditDocument,
            onDeleteDocument = viewModel::onDeleteDocument,
        )
    }
}

/** The Prep tab's dialogs: add/edit document, delete document, edit item text. */
@Composable
fun PrepDialogs(viewModel: PrepViewModel) {
    val documentForm by viewModel.documentForm.collectAsStateWithLifecycle()
    documentForm?.let { form ->
        DocumentFormDialog(
            state = form,
            onNameChange = viewModel::onDocumentNameChange,
            onTypeChange = viewModel::onDocumentTypeChange,
            onDetailChange = viewModel::onDocumentDetailChange,
            onSave = viewModel::onSaveDocument,
            onDismiss = viewModel::onDismissDocumentForm,
        )
    }

    val toDelete by viewModel.documentToDelete.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    toDelete?.let { id ->
        val name = (state as? PrepUiState.Loaded)?.documents?.firstOrNull { it.id == id }?.name.orEmpty()
        DeleteDocumentDialog(
            name = name,
            onConfirm = viewModel::onConfirmDeleteDocument,
            onDismiss = viewModel::onDismissDeleteDocument,
        )
    }

    val editText by viewModel.editText.collectAsStateWithLifecycle()
    editText?.let { edit ->
        EditItemTextDialog(
            state = edit,
            onTextChange = viewModel::onEditTextChange,
            onSave = viewModel::onSaveItemText,
            onDismiss = viewModel::onDismissEditText,
        )
    }
}

private fun PrepMessage.messageRes(): Int = when (this) {
    PrepMessage.FILE_TOO_LARGE -> R.string.prep_file_too_large
    PrepMessage.FILE_UNSUPPORTED -> R.string.prep_file_unsupported
    PrepMessage.FILE_NOT_READABLE -> R.string.prep_file_not_readable
    PrepMessage.FILE_MISSING -> R.string.prep_file_missing
}
