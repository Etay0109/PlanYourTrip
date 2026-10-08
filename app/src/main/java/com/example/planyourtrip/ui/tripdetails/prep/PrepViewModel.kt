package com.example.planyourtrip.ui.tripdetails.prep

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.document.DocumentFileError
import com.example.planyourtrip.data.document.DocumentFileStore
import com.example.planyourtrip.data.document.DocumentInput
import com.example.planyourtrip.data.document.DocumentKind
import com.example.planyourtrip.data.document.DocumentRepository
import com.example.planyourtrip.data.document.DocumentTooLargeException
import com.example.planyourtrip.data.document.DocumentType
import com.example.planyourtrip.data.document.ViewFile
import com.example.planyourtrip.data.document.checkPickedFile
import com.example.planyourtrip.data.document.defaultDocumentName
import com.example.planyourtrip.data.document.documentKind
import com.example.planyourtrip.data.trip.ChecklistItem
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.ChecklistRepository
import com.example.planyourtrip.data.trip.DeletedChecklistItem
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.common.WhileUiSubscribed
import com.example.planyourtrip.ui.navigation.TripDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * The Prep tab of one trip: both checklists (synced) and the travel documents (this device only). Created with the
 * TripDetails back stack entry as owner, so it reads [TripDetails] from the SavedStateHandle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrepViewModel(
    private val tripId: Long,
    tripRepository: TripRepository,
    private val checklistRepository: ChecklistRepository,
    private val documentRepository: DocumentRepository,
    private val fileStore: DocumentFileStore,
    private val authRepository: AuthRepository,
    private val clock: Clock,
    /** Where the progress and rows are computed; tests pass their test dispatcher. */
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    val uiState: StateFlow<PrepUiState> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(PrepUiState.NotFound)
            combine(
                tripRepository.observeTrip(tripId, user.uid),
                checklistRepository.observeItems(tripId, user.uid),
                documentRepository.observeDocuments(tripId, user.uid),
            ) { trip, items, documents ->
                if (trip == null) return@combine PrepUiState.NotFound
                val today = LocalDate.now(clock)
                val progress = prepProgress(items)
                PrepUiState.Loaded(
                    destination = trip.destination,
                    startDate = trip.startDate,
                    endDate = trip.endDate,
                    progress = progress,
                    message = progressMessage(progress, trip.startDate, today),
                    beforeTrip = checklistState(items, ChecklistKind.BEFORE_TRIP, today),
                    packing = checklistState(items, ChecklistKind.PACKING, today),
                    documents = documents.map {
                        DocumentCardState(
                            id = it.id,
                            name = it.name,
                            type = it.type,
                            detail = it.detail,
                            kind = documentKind(it.mimeType) ?: DocumentKind.PDF,
                        )
                    },
                )
            }
        }
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, WhileUiSubscribed, PrepUiState.Loading)

    private val ownerId: String? get() = authRepository.currentUser.value?.uid

    // ---- Checklist ----

    /**
     * Adds an item at the end of [kind]. Returns false (nothing added, the field keeps its text) for blank text or
     * before the trip loaded; true when the field may be cleared for the next item.
     */
    fun onAddItem(kind: ChecklistKind, text: String): Boolean {
        val owner = ownerId ?: return false
        if (text.isBlank() || uiState.value !is PrepUiState.Loaded) return false
        viewModelScope.launch { checklistRepository.addItem(tripId, owner, kind, text) }
        return true
    }

    fun onToggle(id: Long) {
        val owner = ownerId ?: return
        val row = loaded()?.let { it.beforeTrip.rows + it.packing.rows }?.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { checklistRepository.setChecked(id, owner, !row.checked) }
    }

    private var addingSuggestions = false

    /** Adds the suggested [texts] to an empty list; a double tap (or a list that isn't empty) adds nothing. */
    fun onAddSuggested(kind: ChecklistKind, texts: List<String>) {
        val owner = ownerId ?: return
        val state = loaded() ?: return
        if (addingSuggestions || state.checklist(kind).rows.isNotEmpty()) return
        val toAdd = suggestionsToAdd(emptyList(), kind, texts)
        if (toAdd.isEmpty()) return
        addingSuggestions = true
        viewModelScope.launch {
            try {
                checklistRepository.addItems(tripId, owner, kind, toAdd)
            } finally {
                addingSuggestions = false
            }
        }
    }

    private val _editText = MutableStateFlow<EditItemTextState?>(null)

    /** The open "Edit text" dialog, or null. */
    val editText: StateFlow<EditItemTextState?> = _editText.asStateFlow()

    fun onEditItemText(id: Long) {
        val row = loaded()?.let { it.beforeTrip.rows + it.packing.rows }?.firstOrNull { it.id == id } ?: return
        _editText.value = EditItemTextState(id, row.text)
    }

    fun onEditTextChange(text: String) = _editText.update { it?.copy(text = text, isError = false) }

    fun onSaveItemText() {
        val edit = _editText.value ?: return
        if (edit.text.isBlank()) {
            _editText.value = edit.copy(isError = true)
            return
        }
        val owner = ownerId ?: return
        _editText.value = null
        viewModelScope.launch { checklistRepository.updateText(edit.itemId, owner, edit.text) }
    }

    fun onDismissEditText() {
        _editText.value = null
    }

    /** Sets ([date]) or clears (null) the due date of a "Before the trip" item. */
    fun onSetDueDate(id: Long, date: LocalDate?) {
        val owner = ownerId ?: return
        viewModelScope.launch { checklistRepository.setDueDate(id, owner, date) }
    }

    private val _deletedItem = MutableStateFlow<DeletedChecklistItem?>(null)

    /** An item that was just deleted: the screen shows "Item deleted" with "Undo". */
    val deletedItem: StateFlow<DeletedChecklistItem?> = _deletedItem.asStateFlow()

    fun onDeleteItem(id: Long) {
        val owner = ownerId ?: return
        viewModelScope.launch {
            checklistRepository.deleteItem(id, owner)?.let { _deletedItem.value = it }
        }
    }

    /** "Undo": the item comes back at its place in the list. */
    fun onUndoDeleteItem(deleted: DeletedChecklistItem) {
        _deletedItem.compareAndSet(deleted, null)
        val owner = ownerId ?: return
        viewModelScope.launch { checklistRepository.restoreItem(deleted, owner) }
    }

    /** The message went away without "Undo": the delete stays. */
    fun onDeleteItemMessageDismissed(deleted: DeletedChecklistItem) {
        _deletedItem.compareAndSet(deleted, null)
    }

    // ---- Documents ----

    private val _documentForm = MutableStateFlow<DocumentFormState?>(null)

    /** The open add/edit document form, or null. */
    val documentForm: StateFlow<DocumentFormState?> = _documentForm.asStateFlow()

    private val _message = MutableStateFlow<PrepMessage?>(null)

    /** A snackbar message (file too large, unsupported, …), cleared with [onMessageShown]. */
    val message: StateFlow<PrepMessage?> = _message.asStateFlow()

    fun onMessageShown() {
        _message.value = null
    }

    /** A file came back from the system picker: checked first, then the form opens with its name. */
    fun onFilePicked(uri: String) {
        if (ownerId == null || uiState.value !is PrepUiState.Loaded) return
        viewModelScope.launch {
            val picked = try {
                fileStore.inspect(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Logged by the file store.
                _message.value = PrepMessage.FILE_NOT_READABLE
                return@launch
            }
            when (checkPickedFile(picked.mimeType, picked.sizeBytes)) {
                DocumentFileError.UNSUPPORTED_TYPE -> _message.value = PrepMessage.FILE_UNSUPPORTED
                DocumentFileError.TOO_LARGE -> _message.value = PrepMessage.FILE_TOO_LARGE
                null -> _documentForm.value = DocumentFormState(
                    pickedUri = uri,
                    mimeType = picked.mimeType,
                    name = defaultDocumentName(picked.displayName),
                )
            }
        }
    }

    /** The card menu's "Edit": the same form, prefilled (name, type, detail line). */
    fun onEditDocument(id: Long) {
        val document = loaded()?.documents?.firstOrNull { it.id == id } ?: return
        _documentForm.value = DocumentFormState(
            documentId = id,
            name = document.name,
            type = document.type,
            detail = document.detail.orEmpty(),
        )
    }

    fun onDocumentNameChange(name: String) = _documentForm.update { it?.copy(name = name, nameError = false) }

    fun onDocumentTypeChange(type: DocumentType) =
        _documentForm.update { it?.copy(type = type) }

    fun onDocumentDetailChange(detail: String) = _documentForm.update { it?.copy(detail = detail) }

    fun onDismissDocumentForm() {
        if (_documentForm.value?.isSaving == true) return
        _documentForm.value = null
    }

    /** Validates the name, then copies the file (new document) or stores the changes. Double taps save once. */
    fun onSaveDocument() {
        val form = _documentForm.value ?: return
        if (form.isSaving) return
        if (form.name.isBlank()) {
            _documentForm.value = form.copy(nameError = true)
            return
        }
        val owner = ownerId ?: return
        _documentForm.value = form.copy(isSaving = true)
        val input = DocumentInput(form.name, form.type, form.detail)
        viewModelScope.launch {
            val documentId = form.documentId
            if (documentId != null) {
                documentRepository.updateDocument(documentId, owner, input)
                _documentForm.value = null
                return@launch
            }
            try {
                documentRepository.addDocument(
                    tripId, owner, input, checkNotNull(form.pickedUri), checkNotNull(form.mimeType),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: DocumentTooLargeException) {
                _message.value = PrepMessage.FILE_TOO_LARGE
            } catch (e: Exception) {
                // Logged by the file store.
                _message.value = PrepMessage.FILE_NOT_READABLE
            }
            // On a file error the form closes too: the file itself is the problem, so picking again is the way on.
            _documentForm.value = null
        }
    }

    private val _documentToDelete = MutableStateFlow<Long?>(null)

    /** The document whose delete waits for confirmation, or null. */
    val documentToDelete: StateFlow<Long?> = _documentToDelete.asStateFlow()

    fun onDeleteDocument(id: Long) {
        _documentToDelete.value = id
    }

    fun onDismissDeleteDocument() {
        _documentToDelete.value = null
    }

    fun onConfirmDeleteDocument() {
        val id = _documentToDelete.value ?: return
        val owner = ownerId ?: return
        _documentToDelete.value = null
        viewModelScope.launch { documentRepository.deleteDocument(id, owner) }
    }

    private val _pendingView = MutableStateFlow<ViewFile?>(null)

    /** A file to open in the system viewer; the screen starts it and calls [onViewLaunched]. */
    val pendingView: StateFlow<ViewFile?> = _pendingView.asStateFlow()

    fun onViewDocument(id: Long) {
        val owner = ownerId ?: return
        viewModelScope.launch {
            val file = documentRepository.fileToView(id, owner)
            if (file == null) {
                _message.value = PrepMessage.FILE_MISSING
            } else {
                _pendingView.value = file
            }
        }
    }

    fun onViewLaunched() {
        _pendingView.value = null
    }

    private fun loaded(): PrepUiState.Loaded? = uiState.value as? PrepUiState.Loaded

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                val container = appContainer()
                PrepViewModel(
                    tripId = handle.toRoute<TripDetails>().tripId,
                    tripRepository = container.tripRepository,
                    checklistRepository = container.checklistRepository,
                    documentRepository = container.documentRepository,
                    fileStore = container.documentFileStore,
                    authRepository = container.authRepository,
                    clock = container.clock,
                )
            }
        }
    }
}

private fun checklistState(items: List<ChecklistItem>, kind: ChecklistKind, today: LocalDate) = ChecklistState(
    kind = kind,
    rows = itemsOf(items, kind).map {
        ChecklistRowState(
            id = it.id,
            text = it.text,
            checked = it.checked,
            dueDate = it.dueDate,
            dueState = dueState(it, today),
        )
    },
    count = listCount(items, kind),
)
