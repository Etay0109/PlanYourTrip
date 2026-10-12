package com.example.planyourtrip.ui.common.share

import com.example.planyourtrip.FakeActivityRepository
import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeExpenseRepository
import com.example.planyourtrip.FakeTravelerRepository
import com.example.planyourtrip.FakeTripPdfExporter
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.ME
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.data.trip.TripColor
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class ShareTripViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private val rome = Trip(1, "Rome, Italy", nov(12), nov(17), TripColor.SKY, budgetAgorot = 600_000)
    private val othersTrip = Trip(2, "Paris", nov(1), nov(3), TripColor.MINT)

    private val auth = FakeAuthRepository(AuthUser("u1", "Yael", "yael@example.com"))
    private val exporter = FakeTripPdfExporter()
    private lateinit var trips: FakeTripRepository
    private lateinit var viewModel: ShareTripViewModel
    private lateinit var travelers: FakeTravelerRepository
    private lateinit var expenses: FakeExpenseRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trips = FakeTripRepository(listOf("u1" to rome, "u2" to othersTrip))
        expenses = FakeExpenseRepository(
            trips,
            listOf(Expense(1, 1, "Hotel", 250_000, ExpenseCategory.HOTELS, nov(12), paidBy = ME, splitBetween = listOf(ME))),
        )
        val activities = FakeActivityRepository(
            trips,
            listOf(TripActivity(1, 1, nov(12), LocalTime.of(10, 0), "Colosseum", ActivityType.ACTIVITY)),
        )
        travelers = FakeTravelerRepository(trips, expenses)
        viewModel = ShareTripViewModel(auth, trips, expenses, activities, exporter, travelers, dispatcher)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = viewModel.uiState.value

    @Test
    fun `share opens the dialog with costs included`() {
        assertNull(state.dialog)
        viewModel.onShareTrip(1)
        assertEquals(ShareDialogState(tripId = 1, includeCosts = true), state.dialog)
    }

    @Test
    fun `dismiss closes the dialog without creating anything`() {
        viewModel.onShareTrip(1)
        viewModel.onDismiss()
        assertNull(state.dialog)
        assertTrue(exporter.exports.isEmpty())
    }

    @Test
    fun `costs in the PDF count only my share of shared expenses`() = runTest(dispatcher) {
        travelers.travelers.value = listOf(Traveler(1, 1, "noa", "Noa", TravelerColor.ROSE))
        // The hotel (₪2,500) is split with Noa: ₪1,250 is mine.
        expenses.expenses.value = expenses.expenses.value.map { it.copy(paidBy = "noa", splitBetween = listOf(ME, "noa")) }

        viewModel.onShareTrip(1)
        viewModel.onCreatePdf()

        assertEquals(125_000L, exporter.exports.single().first.costs!!.spentAgorot)
    }

    @Test
    fun `create with costs exports the full content and hands it to the share sheet`() = runTest(dispatcher) {
        viewModel.onShareTrip(1)
        viewModel.onCreatePdf()

        val (content, fileName) = exporter.exports.single()
        assertEquals("Rome-Italy-12-17-Nov-2026.pdf", fileName)
        assertEquals(250_000L, content.costs!!.spentAgorot)
        assertEquals("Colosseum", content.days!![0].activities.single().title)
        assertNull(state.dialog)
        assertEquals(SharedPdf("content://test/Rome-Italy-12-17-Nov-2026.pdf"), state.pendingShare)

        viewModel.onShareLaunched()
        assertNull(state.pendingShare)
    }

    @Test
    fun `create without costs exports no money information`() = runTest(dispatcher) {
        viewModel.onShareTrip(1)
        viewModel.onIncludeCostsChange(false)
        assertFalse(state.dialog!!.includeCosts)
        viewModel.onCreatePdf()

        val content = exporter.exports.single().first
        assertNull(content.costs)
        assertNotNull(content.days)
    }

    @Test
    fun `while creating - spinner, double tap creates once, dismiss and switch are ignored`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        exporter.gate = gate
        viewModel.onShareTrip(1)
        viewModel.onCreatePdf()
        assertTrue(state.dialog!!.isCreating)

        viewModel.onCreatePdf()
        viewModel.onDismiss()
        viewModel.onIncludeCostsChange(false)
        assertTrue(state.dialog!!.isCreating)
        assertTrue(state.dialog!!.includeCosts)

        gate.complete(Unit)
        assertEquals(1, exporter.exports.size)
        assertNull(state.dialog)
        assertNotNull(state.pendingShare)
    }

    @Test
    fun `a failure shows the error and try again succeeds`() = runTest(dispatcher) {
        exporter.fail = true
        viewModel.onShareTrip(1)
        viewModel.onCreatePdf()
        assertEquals(ShareDialogState(tripId = 1, isCreating = false, failed = true), state.dialog)
        assertNull(state.pendingShare)

        exporter.fail = false
        viewModel.onCreatePdf()
        assertNull(state.dialog)
        assertNotNull(state.pendingShare)
    }

    @Test
    fun `another account's trip is never exported`() = runTest(dispatcher) {
        viewModel.onShareTrip(2)
        viewModel.onCreatePdf()
        assertTrue(exporter.exports.isEmpty())
        assertTrue(state.dialog!!.failed)
    }

    @Test
    fun `signed out - nothing is exported`() = runTest(dispatcher) {
        auth.currentUser.value = null
        viewModel.onShareTrip(1)
        viewModel.onCreatePdf()
        assertTrue(exporter.exports.isEmpty())
        assertTrue(state.dialog!!.failed)
    }
}
