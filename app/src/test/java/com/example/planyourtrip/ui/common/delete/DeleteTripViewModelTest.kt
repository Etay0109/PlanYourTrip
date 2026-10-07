package com.example.planyourtrip.ui.common.delete

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeDocumentFileStore
import com.example.planyourtrip.FakeDocumentRepository
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.document.DocumentInput
import com.example.planyourtrip.data.document.DocumentType
import com.example.planyourtrip.data.document.PickedFile
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteTripViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private val rome = Trip(1, "Rome, Italy", nov(12), nov(17), TripColor.SKY)
    private val athens = Trip(2, "Athens, Greece", nov(20), nov(24), TripColor.PEACH)
    private val othersTrip = Trip(3, "Paris", nov(1), nov(3), TripColor.MINT)

    private val auth = FakeAuthRepository(AuthUser("u1", "Etay", "etay@example.com"))
    private val files = FakeDocumentFileStore()
    private lateinit var trips: FakeTripRepository
    private lateinit var documents: FakeDocumentRepository
    private lateinit var viewModel: DeleteTripViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trips = FakeTripRepository(listOf("u1" to rome, "u1" to athens, "u2" to othersTrip))
        documents = FakeDocumentRepository(trips, files)
        viewModel = DeleteTripViewModel(auth, trips, documents)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = viewModel.uiState.value

    private fun ownTrips() = trips.tripsOf("u1").map { it.id }

    private suspend fun addDocument(tripId: Long, owner: String = "u1", uri: String = "content://picked/$tripId") {
        files.picked[uri] = PickedFile("ticket.pdf", "application/pdf", 1_000)
        documents.addDocument(tripId, owner, DocumentInput("Ticket", DocumentType.TICKET, null), uri, "application/pdf")
    }

    @Test
    fun `delete opens the dialog naming the trip`() {
        viewModel.onDeleteTrip(1, "Rome, Italy")

        assertEquals(DeleteDialogState(tripId = 1, destination = "Rome, Italy"), state.dialog)
        assertEquals(listOf(1L, 2L), ownTrips()) // nothing deleted before the confirmation
    }

    @Test
    fun `cancel closes the dialog and keeps the trip`() {
        viewModel.onDeleteTrip(1, "Rome, Italy")

        viewModel.onDismiss()

        assertNull(state.dialog)
        assertNull(state.message)
        assertEquals(listOf(1L, 2L), ownTrips())
        assertEquals(0, trips.deletes)
    }

    @Test
    fun `confirm deletes the trip and says so`() {
        viewModel.onDeleteTrip(1, "Rome, Italy")

        viewModel.onConfirm()

        assertNull(state.dialog)
        assertEquals(DeleteTripMessage.DELETED, state.message)
        assertEquals(listOf(2L), ownTrips())
        viewModel.onMessageShown()
        assertNull(state.message)
    }

    @Test
    fun `a double tap on Delete deletes once, and the dialog stays until it is done`() {
        val gate = CompletableDeferred<Unit>()
        trips.deleteGate = gate
        viewModel.onDeleteTrip(1, "Rome, Italy")

        viewModel.onConfirm()
        viewModel.onConfirm()
        viewModel.onDismiss() // ignored while deleting
        assertTrue(state.dialog?.isDeleting == true)

        gate.complete(Unit)

        assertEquals(1, trips.deletes)
        assertNull(state.dialog)
        assertEquals(DeleteTripMessage.DELETED, state.message)
    }

    @Test
    fun `deleting removes the trip's document files and keeps the other trips' files`() = runTest(dispatcher) {
        addDocument(1, uri = "content://picked/a")
        addDocument(1, uri = "content://picked/b")
        addDocument(2, uri = "content://picked/c")
        val athensFile = documents.rows.value.single { it.tripId == 2L }.document.fileName
        assertEquals(3, files.stored.size)

        viewModel.onDeleteTrip(1, "Rome, Italy")
        viewModel.onConfirm()

        assertEquals(setOf(athensFile), files.stored.keys)
        assertEquals(listOf(2L), documents.rows.value.map { it.tripId })
    }

    @Test
    fun `another account's trip is never deleted`() {
        viewModel.onDeleteTrip(3, "Paris")

        viewModel.onConfirm()

        assertEquals(DeleteTripMessage.FAILED, state.message)
        assertEquals(listOf(3L), trips.tripsOf("u2").map { it.id })
    }

    @Test
    fun `without a signed-in user nothing is deleted`() {
        auth.currentUser.value = null
        viewModel.onDeleteTrip(1, "Rome, Italy")

        viewModel.onConfirm()

        assertEquals(DeleteTripMessage.FAILED, state.message)
        assertEquals(listOf(1L, 2L), ownTrips())
    }

    @Test
    fun `a failed delete says so and keeps the trip`() {
        trips.failDelete = true
        viewModel.onDeleteTrip(1, "Rome, Italy")

        viewModel.onConfirm()

        assertNull(state.dialog)
        assertEquals(DeleteTripMessage.FAILED, state.message)
        assertEquals(listOf(1L, 2L), ownTrips())
    }

    @Test
    fun `the trip screen only confirms, and Home deletes the confirmed trip`() {
        viewModel.onDeleteTrip(1, "Rome, Italy")

        assertEquals(1L, viewModel.takeConfirmed())
        assertNull(state.dialog)
        assertEquals(listOf(1L, 2L), ownTrips()) // nothing deleted on the trip screen
        assertNull(viewModel.takeConfirmed()) // once

        val home = DeleteTripViewModel(auth, trips, documents)
        home.deleteConfirmed(1)

        assertEquals(listOf(2L), ownTrips())
        assertEquals(DeleteTripMessage.DELETED, home.uiState.value.message)
        assertTrue(home.uiState.value.dialog == null)
    }
}
