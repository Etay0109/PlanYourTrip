package com.example.planyourtrip.ui.tripdetails.prep

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeChecklistRepository
import com.example.planyourtrip.FakeDocumentFileStore
import com.example.planyourtrip.FakeDocumentRepository
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.document.DocumentKind
import com.example.planyourtrip.data.document.DocumentType
import com.example.planyourtrip.data.document.MAX_DOCUMENT_BYTES
import com.example.planyourtrip.data.document.PickedFile
import com.example.planyourtrip.data.trip.ChecklistItem
import com.example.planyourtrip.data.trip.ChecklistKind.BEFORE_TRIP
import com.example.planyourtrip.data.trip.ChecklistKind.PACKING
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class PrepViewModelTest {

    private val today = LocalDate.of(2026, 10, 7)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val dispatcher = UnconfinedTestDispatcher()
    private val auth = FakeAuthRepository(AuthUser("u1", "Yael", "yael@example.com"))

    private val rome = Trip(1, "Rome, Italy", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 24), TripColor.SKY)
    private val othersTrip = Trip(2, "Paris", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), TripColor.MINT)

    private lateinit var trips: FakeTripRepository
    private lateinit var checklist: FakeChecklistRepository
    private lateinit var files: FakeDocumentFileStore
    private lateinit var documents: FakeDocumentRepository

    private val passport = ChecklistItem(1, 1, BEFORE_TRIP, "Check passport", checked = true, createdAt = 1)
    private val checkIn = ChecklistItem(2, 1, BEFORE_TRIP, "Online check-in", dueDate = today.minusDays(1), createdAt = 2)
    private val charger = ChecklistItem(3, 1, PACKING, "Charger", createdAt = 3)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trips = FakeTripRepository(listOf("u1" to rome, "u2" to othersTrip))
        checklist = FakeChecklistRepository(trips, listOf(passport, checkIn, charger))
        files = FakeDocumentFileStore()
        documents = FakeDocumentRepository(trips, files)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(tripId: Long = 1) =
        PrepViewModel(tripId, trips, checklist, documents, files, auth, clock, dispatcher).also {
            it.uiState.launchIn(backgroundScope)
        }

    private fun PrepViewModel.loaded() = uiState.value as PrepUiState.Loaded

    // ---- Checklist ----

    @Test
    fun loads_progressCountsAndDueStates() = runTest(dispatcher) {
        val state = vm().loaded()
        assertEquals(PrepProgress(1, 3), state.progress)
        assertEquals(ProgressMessage.LeftBeforeTrip(2), state.message)
        assertEquals(ListCount(1, 2), state.beforeTrip.count)
        assertEquals(ListCount(0, 1), state.packing.count)
        assertEquals(listOf(passport.id, checkIn.id), state.beforeTrip.rows.map { it.id })
        assertEquals(DueState.OVERDUE, state.beforeTrip.rows[1].dueState)
        assertEquals("Rome, Italy", state.destination)
    }

    @Test
    fun noItems_showsTheEmptyMessage() = runTest(dispatcher) {
        checklist.items.value = emptyList()
        val state = vm().loaded()
        assertEquals(PrepProgress(0, 0), state.progress)
        assertEquals(ProgressMessage.Empty, state.message)
    }

    @Test
    fun allDone() = runTest(dispatcher) {
        checklist.items.value = listOf(passport, charger.copy(checked = true))
        assertEquals(ProgressMessage.AllDone, vm().loaded().message)
    }

    @Test
    fun toggle_updatesRowChipCountsAndProgress() = runTest(dispatcher) {
        val vm = vm()
        vm.onToggle(checkIn.id)
        val state = vm.loaded()
        assertTrue(state.beforeTrip.rows.first { it.id == checkIn.id }.checked)
        // Done now, so no longer overdue.
        assertEquals(DueState.UPCOMING, state.beforeTrip.rows.first { it.id == checkIn.id }.dueState)
        assertEquals(ListCount(2, 2), state.beforeTrip.count)
        assertEquals(PrepProgress(2, 3), state.progress)

        vm.onToggle(passport.id)
        assertFalse(vm.loaded().beforeTrip.rows.first { it.id == passport.id }.checked)
        assertEquals(PrepProgress(1, 3), vm.loaded().progress)
    }

    @Test
    fun addItem_goesToTheEndOfItsList() = runTest(dispatcher) {
        val vm = vm()
        assertTrue(vm.onAddItem(BEFORE_TRIP, "  Exchange money "))
        assertTrue(vm.onAddItem(PACKING, "Adapter"))
        assertTrue(vm.onAddItem(BEFORE_TRIP, "Book transfer"))
        val state = vm.loaded()
        assertEquals(
            listOf("Check passport", "Online check-in", "Exchange money", "Book transfer"),
            state.beforeTrip.rows.map { it.text },
        )
        assertEquals(listOf("Charger", "Adapter"), state.packing.rows.map { it.text })
        assertEquals(PrepProgress(1, 6), state.progress)
    }

    @Test
    fun addItem_blankIsRefused() = runTest(dispatcher) {
        val vm = vm()
        assertFalse(vm.onAddItem(PACKING, "   "))
        assertEquals(3, checklist.items.value.size)
    }

    @Test
    fun suggestedItems_addedToAnEmptyListOnce() = runTest(dispatcher) {
        checklist.items.value = listOf(passport)
        val vm = vm()
        val suggestions = listOf("Phone charger", "Power adapter", "Medicines")
        vm.onAddSuggested(PACKING, suggestions)
        vm.onAddSuggested(PACKING, suggestions) // double tap / list no longer empty
        assertEquals(1, checklist.addItemsCalls)
        assertEquals(suggestions, vm.loaded().packing.rows.map { it.text })
        assertEquals(ListCount(0, 3), vm.loaded().packing.count)
    }

    @Test
    fun suggestedItems_neverAddedToAListWithItems() = runTest(dispatcher) {
        val vm = vm()
        vm.onAddSuggested(BEFORE_TRIP, listOf("Passport"))
        assertEquals(0, checklist.addItemsCalls)
        assertEquals(2, vm.loaded().beforeTrip.rows.size)
    }

    @Test
    fun suggestedItems_aDoubleTapWhileAddingAddsOnce() = runTest(dispatcher) {
        checklist.items.value = emptyList()
        val gate = CompletableDeferred<Unit>()
        val slow = object : com.example.planyourtrip.data.trip.ChecklistRepository by checklist {
            override suspend fun addItems(
                tripId: Long, ownerId: String, kind: com.example.planyourtrip.data.trip.ChecklistKind, texts: List<String>,
            ) {
                gate.await()
                checklist.addItems(tripId, ownerId, kind, texts)
            }
        }
        val vm = PrepViewModel(1, trips, slow, documents, files, auth, clock, dispatcher).also {
            it.uiState.launchIn(backgroundScope)
        }
        vm.onAddSuggested(PACKING, listOf("Charger"))
        vm.onAddSuggested(PACKING, listOf("Charger"))
        gate.complete(Unit)
        assertEquals(1, checklist.addItemsCalls)
        assertEquals(listOf("Charger"), vm.loaded().packing.rows.map { it.text })
    }

    @Test
    fun editText() = runTest(dispatcher) {
        val vm = vm()
        vm.onEditItemText(charger.id)
        assertEquals("Charger", vm.editText.value?.text)
        vm.onEditTextChange("  ")
        vm.onSaveItemText()
        assertTrue(vm.editText.value!!.isError)
        vm.onEditTextChange("USB-C charger ")
        vm.onSaveItemText()
        assertNull(vm.editText.value)
        assertEquals("USB-C charger", vm.loaded().packing.rows.single().text)
    }

    @Test
    fun dueDate_setAndClear_onlyForBeforeTheTrip() = runTest(dispatcher) {
        val vm = vm()
        vm.onSetDueDate(passport.id, today.plusDays(2))
        assertEquals(today.plusDays(2), vm.loaded().beforeTrip.rows.first().dueDate)
        vm.onSetDueDate(checkIn.id, null)
        assertNull(vm.loaded().beforeTrip.rows[1].dueDate)
        assertEquals(DueState.NONE, vm.loaded().beforeTrip.rows[1].dueState)
        vm.onSetDueDate(charger.id, today)
        assertNull(vm.loaded().packing.rows.single().dueDate)
    }

    @Test
    fun delete_undoRestoresAtItsPlace() = runTest(dispatcher) {
        val vm = vm()
        vm.onDeleteItem(passport.id)
        assertEquals(listOf("Online check-in"), vm.loaded().beforeTrip.rows.map { it.text })
        val deleted = assertNotNull(vm.deletedItem.value).let { vm.deletedItem.value!! }
        vm.onUndoDeleteItem(deleted)
        assertNull(vm.deletedItem.value)
        assertEquals(listOf("Check passport", "Online check-in"), vm.loaded().beforeTrip.rows.map { it.text })
        assertTrue(vm.loaded().beforeTrip.rows.first().checked)
    }

    @Test
    fun delete_dismissKeepsTheDelete() = runTest(dispatcher) {
        val vm = vm()
        vm.onDeleteItem(charger.id)
        vm.onDeleteItemMessageDismissed(vm.deletedItem.value!!)
        assertNull(vm.deletedItem.value)
        assertTrue(vm.loaded().packing.rows.isEmpty())
    }

    @Test
    fun anotherAccountsTrip_showsNothingAndChangesNothing() = runTest(dispatcher) {
        checklist.items.value = listOf(ChecklistItem(9, 2, PACKING, "Their item"))
        val vm = vm(tripId = 2)
        assertEquals(PrepUiState.NotFound, vm.uiState.value)
        assertFalse(vm.onAddItem(PACKING, "Mine"))
        vm.onToggle(9)
        assertFalse(checklist.items.value.single().checked)
    }

    @Test
    fun signedOut_showsNothing() = runTest(dispatcher) {
        auth.currentUser.value = null
        assertEquals(PrepUiState.NotFound, vm().uiState.value)
    }

    // ---- Documents ----

    private val pdfUri = "content://picker/flight.pdf"

    @Test
    fun pickPdf_formPrefilled_saveAddsTheDocument() = runTest(dispatcher) {
        files.picked[pdfUri] = PickedFile("Flight tickets.pdf", "application/pdf", 250_000)
        val vm = vm()
        vm.onFilePicked(pdfUri)
        val form = assertNotNull(vm.documentForm.value).let { vm.documentForm.value!! }
        assertEquals("Flight tickets", form.name)
        assertEquals(DocumentType.OTHER, form.type)
        assertTrue(form.isNew)

        vm.onDocumentTypeChange(DocumentType.FLIGHT)
        vm.onDocumentDetailChange("TLV → FCO · 19 Oct")
        vm.onSaveDocument()
        assertNull(vm.documentForm.value)
        val card = vm.loaded().documents.single()
        assertEquals("Flight tickets", card.name)
        assertEquals(DocumentType.FLIGHT, card.type)
        assertEquals("TLV → FCO · 19 Oct", card.detail)
        assertEquals(DocumentKind.PDF, card.kind)
        assertEquals(1, files.stored.size)
    }

    @Test
    fun pickTooLargeFile_showsMessageAndNoForm() = runTest(dispatcher) {
        files.picked[pdfUri] = PickedFile("big.pdf", "application/pdf", MAX_DOCUMENT_BYTES + 1)
        val vm = vm()
        vm.onFilePicked(pdfUri)
        assertNull(vm.documentForm.value)
        assertEquals(PrepMessage.FILE_TOO_LARGE, vm.message.value)
        vm.onMessageShown()
        assertNull(vm.message.value)
    }

    @Test
    fun pickUnsupportedFile_showsMessageAndNoForm() = runTest(dispatcher) {
        files.picked["content://picker/notes.txt"] = PickedFile("notes.txt", "text/plain", 100)
        val vm = vm()
        vm.onFilePicked("content://picker/notes.txt")
        assertNull(vm.documentForm.value)
        assertEquals(PrepMessage.FILE_UNSUPPORTED, vm.message.value)
        assertTrue(documents.rows.value.isEmpty())
    }

    @Test
    fun fileWithUnknownSize_tooLargeWhileCopying() = runTest(dispatcher) {
        files.picked[pdfUri] = PickedFile("scan.pdf", "application/pdf", null)
        files.actualSizes[pdfUri] = MAX_DOCUMENT_BYTES + 10
        val vm = vm()
        vm.onFilePicked(pdfUri)
        vm.onSaveDocument()
        assertEquals(PrepMessage.FILE_TOO_LARGE, vm.message.value)
        assertNull(vm.documentForm.value)
        assertTrue(documents.rows.value.isEmpty())
        assertTrue(files.stored.isEmpty())
    }

    @Test
    fun unreadableFile_showsMessage() = runTest(dispatcher) {
        val vm = vm()
        vm.onFilePicked("content://picker/gone")
        assertEquals(PrepMessage.FILE_NOT_READABLE, vm.message.value)

        files.picked[pdfUri] = PickedFile("a.pdf", "application/pdf", 10)
        files.failCopy = true
        vm.onMessageShown()
        vm.onFilePicked(pdfUri)
        vm.onSaveDocument()
        assertEquals(PrepMessage.FILE_NOT_READABLE, vm.message.value)
        assertTrue(documents.rows.value.isEmpty())
    }

    @Test
    fun blankName_isRefused() = runTest(dispatcher) {
        files.picked[pdfUri] = PickedFile(null, "image/png", 10)
        val vm = vm()
        vm.onFilePicked(pdfUri)
        vm.onSaveDocument()
        assertTrue(vm.documentForm.value!!.nameError)
        vm.onDocumentNameChange("Insurance")
        assertFalse(vm.documentForm.value!!.nameError)
        vm.onSaveDocument()
        assertEquals(DocumentKind.PHOTO, vm.loaded().documents.single().kind)
    }

    @Test
    fun save_doubleTapSavesOnce() = runTest(dispatcher) {
        files.picked[pdfUri] = PickedFile("Hotel.pdf", "application/pdf", 10)
        val gate = CompletableDeferred<Unit>()
        val slowFiles = object : com.example.planyourtrip.data.document.DocumentFileStore by files {
            override suspend fun copyIn(uri: String, fileName: String, maxBytes: Long): Long {
                gate.await()
                return files.copyIn(uri, fileName, maxBytes)
            }
        }
        val slowDocuments = FakeDocumentRepository(trips, slowFiles)
        val vm = PrepViewModel(1, trips, checklist, slowDocuments, files, auth, clock, dispatcher).also {
            it.uiState.launchIn(backgroundScope)
        }
        vm.onFilePicked(pdfUri)
        vm.onSaveDocument()
        assertTrue(vm.documentForm.value!!.isSaving)
        vm.onSaveDocument()
        vm.onDismissDocumentForm() // ignored while saving
        assertNotNull(vm.documentForm.value)
        gate.complete(Unit)
        assertEquals(1, slowDocuments.adds)
        assertNull(vm.documentForm.value)
    }

    @Test
    fun editDocument_renamesAndChangesTheDetail() = runTest(dispatcher) {
        files.picked[pdfUri] = PickedFile("x.pdf", "application/pdf", 10)
        val vm = vm()
        vm.onFilePicked(pdfUri)
        vm.onSaveDocument()
        val id = vm.loaded().documents.single().id

        vm.onEditDocument(id)
        val form = vm.documentForm.value!!
        assertFalse(form.isNew)
        assertEquals("x", form.name)
        vm.onDocumentNameChange("Hotel booking")
        vm.onDocumentDetailChange("Hotel Artemide · 5n")
        vm.onSaveDocument()
        val card = vm.loaded().documents.single()
        assertEquals("Hotel booking", card.name)
        assertEquals("Hotel Artemide · 5n", card.detail)
        assertEquals(1, documents.adds)
    }

    @Test
    fun deleteDocument_needsConfirmation_andRemovesTheFile() = runTest(dispatcher) {
        files.picked[pdfUri] = PickedFile("x.pdf", "application/pdf", 10)
        val vm = vm()
        vm.onFilePicked(pdfUri)
        vm.onSaveDocument()
        val id = vm.loaded().documents.single().id

        vm.onDeleteDocument(id)
        assertEquals(id, vm.documentToDelete.value)
        vm.onDismissDeleteDocument()
        assertEquals(1, vm.loaded().documents.size)

        vm.onDeleteDocument(id)
        vm.onConfirmDeleteDocument()
        assertNull(vm.documentToDelete.value)
        assertTrue(vm.loaded().documents.isEmpty())
        assertTrue(files.stored.isEmpty())
    }

    @Test
    fun viewDocument_opensTheStoredFile_orReportsAMissingOne() = runTest(dispatcher) {
        files.picked[pdfUri] = PickedFile("x.pdf", "application/pdf", 10)
        val vm = vm()
        vm.onFilePicked(pdfUri)
        vm.onSaveDocument()
        val id = vm.loaded().documents.single().id

        vm.onViewDocument(id)
        val view = vm.pendingView.value!!
        assertEquals("application/pdf", view.mimeType)
        assertTrue(view.uri.startsWith("content://"))
        vm.onViewLaunched()
        assertNull(vm.pendingView.value)

        files.stored.clear() // the file disappeared
        vm.onViewDocument(id)
        assertNull(vm.pendingView.value)
        assertEquals(PrepMessage.FILE_MISSING, vm.message.value)
    }
}
