package com.example.planyourtrip.ui.home.join

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeInviteRepository
import com.example.planyourtrip.FakeSyncController
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.sharing.InvitePreview
import com.example.planyourtrip.data.sharing.JoinResult
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import kotlinx.coroutines.CompletableDeferred
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

@OptIn(ExperimentalCoroutinesApi::class)
class JoinTripViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val code = "K7M4XP2R"
    private val preview = InvitePreview(
        code, "t1", "Rome, Italy", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 24), "Noa",
    )
    private val auth = FakeAuthRepository(AuthUser("u1", "Etay", "etay@example.com"))
    private val invites = FakeInviteRepository()
    private lateinit var trips: FakeTripRepository
    private lateinit var sync: FakeSyncController
    private lateinit var viewModel: JoinTripViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        invites.previews[code] = preview
        trips = FakeTripRepository()
        sync = FakeSyncController(auth)
        viewModel = JoinTripViewModel(auth, invites, trips, sync)
        viewModel.onOpen()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = viewModel.uiState.value

    @Test
    fun theCodeIsLookedUpOnlyOnceComplete_andThenJoinIsEnabled() {
        viewModel.onCodeChange("K7M4")
        viewModel.onCodeChange("K7M4XP2")
        assertTrue(invites.lookups.isEmpty())
        assertFalse(state.canJoin)

        viewModel.onCodeChange(code)

        assertEquals(listOf(code), invites.lookups)
        assertEquals(CodeLookup.Found(preview), state.lookup)
        assertTrue(state.canJoin)
    }

    @Test
    fun typedTextIsNormalized_likeAPastedMessage() {
        viewModel.onCodeChange("k7m4-xp2r")
        assertEquals(CodeLookup.Found(preview), state.lookup)
    }

    @Test
    fun aWrongExpiredUsedOrCancelledCode_getsOneMessage_andNoJoin() {
        viewModel.onCodeChange("ABCD2345")

        assertEquals(CodeLookup.Invalid, state.lookup)
        assertFalse(state.canJoin)
        viewModel.onJoin()
        assertTrue(invites.joins.isEmpty())
    }

    @Test
    fun editingTheCode_clearsTheTrip() {
        viewModel.onCodeChange(code)
        viewModel.onCodeChange("K7M4XP2")
        assertEquals(CodeLookup.Incomplete, state.lookup)
        assertFalse(state.canJoin)
    }

    @Test
    fun lookupOffline_offersTryAgain() {
        invites.offline = true
        viewModel.onCodeChange(code)
        assertEquals(CodeLookup.Failed, state.lookup)

        invites.offline = false
        viewModel.onRetry()
        assertEquals(CodeLookup.Found(preview), state.lookup)
    }

    @Test
    fun joining_closesTheSheet_saysWhere_andSyncs_onceForADoubleTap() {
        val gate = CompletableDeferred<Unit>()
        invites.joinGate = gate
        viewModel.onCodeChange(code)

        viewModel.onJoin()
        viewModel.onJoin()
        assertTrue(state.isJoining)
        viewModel.onDismiss() // ignored while joining
        assertTrue(state.isOpen)
        gate.complete(Unit)

        assertEquals(1, invites.joins.size)
        assertEquals("Etay", invites.joins.single().second)
        assertFalse(state.isOpen)
        assertEquals("Rome, Italy", state.joinedDestination)
        assertEquals(1, sync.refreshCalls)
        viewModel.onJoinedShown()
        assertNull(state.joinedDestination)
    }

    @Test
    fun theMemberNameIsNeverTheEmail() {
        auth.currentUser.value = AuthUser("u1", null, "etay@example.com")
        viewModel.onCodeChange(code)
        viewModel.onJoin()
        assertFalse(invites.joins.single().second.contains("etay"))
    }

    @Test
    fun alreadyAMember_isSaidSeparately() {
        invites.joinResult = JoinResult.ALREADY_MEMBER
        viewModel.onCodeChange(code)
        viewModel.onJoin()
        assertEquals(JoinError.ALREADY_MEMBER, state.error)
        assertTrue(state.isOpen)
        assertEquals(0, sync.refreshCalls)
    }

    @Test
    fun aTripAlreadyOnThisDevice_isAlreadyMine_withoutAskingTheCloud() {
        trips.trips.value = listOf(
            "u1" to Trip(1, "Rome", preview.startDate, preview.endDate, TripColor.SKY, uuid = "t1"),
        )
        viewModel.onCodeChange(code)
        viewModel.onJoin()
        assertEquals(JoinError.ALREADY_MEMBER, state.error)
        assertTrue(invites.joins.isEmpty())
    }

    @Test
    fun aFullTrip_isSaidSeparately() {
        invites.joinResult = JoinResult.TRIP_FULL
        viewModel.onCodeChange(code)
        viewModel.onJoin()
        assertEquals(JoinError.TRIP_FULL, state.error)
    }

    @Test
    fun aCodeUsedMeanwhile_getsTheOneInvalidMessage() {
        invites.joinResult = JoinResult.INVALID_CODE
        viewModel.onCodeChange(code)
        viewModel.onJoin()
        assertEquals(JoinError.INVALID_CODE, state.error)
        assertEquals(CodeLookup.Invalid, state.lookup)
        assertFalse(state.canJoin)
    }

    @Test
    fun joinFailing_offline_saysSo_andCanBeTriedAgain() {
        invites.joinResult = JoinResult.FAILED
        viewModel.onCodeChange(code)
        viewModel.onJoin()
        assertEquals(JoinError.FAILED, state.error)
        assertTrue(state.canJoin)
    }
}
