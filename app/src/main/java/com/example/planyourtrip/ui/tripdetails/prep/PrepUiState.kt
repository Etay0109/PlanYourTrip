package com.example.planyourtrip.ui.tripdetails.prep

import com.example.planyourtrip.data.document.DocumentKind
import com.example.planyourtrip.data.document.DocumentType
import com.example.planyourtrip.data.trip.ChecklistKind
import java.io.Serializable
import java.time.LocalDate

sealed interface PrepUiState {
    /** Room hasn't answered yet: nothing is drawn. */
    data object Loading : PrepUiState

    /** Not the user's trip (the trip screen shows its own "not found"). */
    data object NotFound : PrepUiState

    data class Loaded(
        val destination: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val progress: PrepProgress,
        val message: ProgressMessage,
        val beforeTrip: ChecklistState,
        val packing: ChecklistState,
        val documents: List<DocumentCardState>,
    ) : PrepUiState {
        fun checklist(kind: ChecklistKind): ChecklistState = when (kind) {
            ChecklistKind.BEFORE_TRIP -> beforeTrip
            ChecklistKind.PACKING -> packing
        }
    }
}

/** One list: its rows in the order they were added and its "(5/8)" count. */
data class ChecklistState(
    val kind: ChecklistKind,
    val rows: List<ChecklistRowState>,
    val count: ListCount,
)

data class ChecklistRowState(
    val id: Long,
    val text: String,
    val checked: Boolean,
    val dueDate: LocalDate?,
    val dueState: DueState,
)

data class DocumentCardState(
    val id: Long,
    val name: String,
    val type: DocumentType,
    val detail: String?,
    /** "PDF" or "Photo" badge. */
    val kind: DocumentKind,
)

/**
 * The add/edit document form. [pickedUri] is set for a new document (the file is copied on Save). Serializable: an
 * open form comes back after process death.
 */
data class DocumentFormState(
    val documentId: Long? = null,
    val pickedUri: String? = null,
    val mimeType: String? = null,
    val name: String = "",
    val type: DocumentType = DocumentType.OTHER,
    val detail: String = "",
    val nameError: Boolean = false,
    val isSaving: Boolean = false,
) : Serializable {
    val isNew: Boolean get() = documentId == null
}

/** The "Edit text" dialog of a checklist item (Serializable: it comes back after process death). */
data class EditItemTextState(val itemId: Long, val text: String, val isError: Boolean = false) : Serializable

/** Snackbar messages of the Prep tab, mapped to strings by the UI. */
enum class PrepMessage { FILE_TOO_LARGE, FILE_UNSUPPORTED, FILE_NOT_READABLE, FILE_MISSING }
