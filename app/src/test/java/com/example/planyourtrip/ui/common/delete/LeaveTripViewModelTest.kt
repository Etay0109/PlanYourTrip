package com.example.planyourtrip.ui.common.delete

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeDocumentFileStore
import com.example.planyourtrip.FakeDocumentRepository
import com.example.planyourtrip.FakeSyncController
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** "Leave trip" instead of "Delete trip" for a shared trip someone else owns. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaveTripViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private val mine = Trip(1, "Rome, Italy", nov(12), nov(17), TripColor.SKY, ownerId = "u1", uuid = "t1")
    private val shared = Trip(
        2, "Athens", nov(20), nov(24), TripColor.PEACH, ownerId = "noa", memberIds = listOf("noa", "u1"), uuid = "t2",
    )

    private val auth = FakeAuthRepository(AuthUser("u1", "Yael", "yael@example.com"))
    private lateinit var trips: FakeTripRepository
    private lateinit var sync: FakeSyncController
    private lateinit var viewModel: DeleteTripViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trips = FakeTripRepository(listOf("u1" to mine, "u1" to shared))
        sync = FakeSyncController(auth)
        viewModel = DeleteTripViewModel(auth, trips, FakeDocumentRepository(trips, FakeDocumentFileStore()), sync)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = viewModel.uiState.value

    @Test
    fun myOwnTrip_isDeleted_aSharedTripOfSomeoneElse_isLeft() {
        viewModel.onRemoveTrip(mine)
        assertFalse(state.dialog!!.leave)
        viewModel.onDismiss()

        viewModel.onRemoveTrip(shared)
        assertTrue(state.dialog!!.leave)
        assertEquals("Athens", state.dialog!!.destination)
    }

    @Test
    fun confirmingLeave_leavesThroughTheSync_andNeverDeletes() {
        viewModel.onRemoveTrip(shared)
        viewModel.onConfirm()

        assertEquals(listOf("t2"), sync.leaveCalls)
        assertEquals(0, trips.deletes)
        assertNull(state.dialog)
        assertEquals(DeleteTripMessage.LEFT, state.message)
    }

    @Test
    fun leavingOffline_saysSo() {
        sync.leaveResult = false
        viewModel.onRemoveTrip(shared)
        viewModel.onConfirm()
        assertEquals(DeleteTripMessage.LEAVE_FAILED, state.message)
    }

    @Test
    fun leaveConfirmedOnTheTripScreen_isDoneOnHome() {
        viewModel.leaveConfirmed(2)
        assertEquals(listOf("t2"), sync.leaveCalls)
        assertEquals(DeleteTripMessage.LEFT, state.message)
    }

    @Test
    fun myOwnTrip_isNeverLeft() {
        viewModel.leaveConfirmed(1)
        assertTrue(sync.leaveCalls.isEmpty())
        assertEquals(DeleteTripMessage.LEAVE_FAILED, state.message)
    }
}
