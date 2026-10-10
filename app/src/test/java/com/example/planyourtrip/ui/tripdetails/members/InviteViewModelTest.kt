package com.example.planyourtrip.ui.tripdetails.members

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeInviteRepository
import com.example.planyourtrip.FakeSyncController
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.sharing.Invite
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.split.Person
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class InviteViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val nowMillis = Instant.parse("2026-10-09T10:00:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(nowMillis), ZoneOffset.UTC)
    private val day = 24 * 60 * 60 * 1000L

    private val rome = Trip(
        1, "Rome, Italy", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 24), TripColor.SKY,
        ownerId = "u1", memberIds = listOf("u1", "noa"), uuid = "t1",
    )
    private val auth = FakeAuthRepository(AuthUser("u1", "Etay", "etay@example.com"))
    private val invites = FakeInviteRepository(now = { nowMillis })
    private lateinit var trips: FakeTripRepository
    private lateinit var sync: FakeSyncController
    private lateinit var viewModel: InviteViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trips = FakeTripRepository(listOf("u1" to rome))
        sync = FakeSyncController(auth)
        viewModel = InviteViewModel(1, trips, auth, invites, sync, clock)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val state get() = viewModel.uiState.value

    private val noa = MemberEntry(Person.Member("noa", "Noa", TravelerColor.ROSE), isOwner = false, joinedOn = null)

    @Test
    fun invite_withoutAnOpenCode_createsOne_withTheOwnersName_expiringInSevenDays() {
        viewModel.onInvite()

        val ready = state.code as InviteCodeState.Ready
        assertEquals(MembersSheetMode.INVITE, state.sheet)
        assertEquals(listOf("t1" to "Etay"), invites.created)
        assertEquals(LocalDate.of(2026, 10, 16), ready.expiresOn)
    }

    @Test
    fun openingTheSheetAgain_showsTheSameOpenCode() {
        viewModel.onInvite()
        val first = (state.code as InviteCodeState.Ready).invite
        viewModel.onDismissSheet()

        viewModel.onInvite()

        assertEquals(first, (state.code as InviteCodeState.Ready).invite)
        assertEquals(1, invites.created.size)
    }

    @Test
    fun aUsedOrExpiredCode_isReplacedByANewOne() {
        // Used, cancelled or expired codes can't be read: the repository finds none.
        invites.current["t1"] = null
        viewModel.onInvite()
        assertEquals(1, invites.created.size)

        // An expired one this device still saw as open (the rules deny it anyway).
        viewModel.onDismissSheet()
        invites.current["t1"] = Invite("OLDC0DE2".replace('0', 'Q'), createdAt = nowMillis - 8 * day)
        viewModel.onInvite()
        assertEquals(2, invites.created.size)
        assertTrue((state.code as InviteCodeState.Ready).invite.isOpenAt(nowMillis))
    }

    @Test
    fun cancel_stopsTheCode_andANewOneCanBeMade() {
        viewModel.onInvite()
        val code = (state.code as InviteCodeState.Ready).invite.code

        viewModel.onCancelCode()

        assertEquals(listOf(code), invites.cancelled)
        assertEquals(InviteCodeState.Cancelled, state.code)
        viewModel.onNewCode()
        assertTrue((state.code as InviteCodeState.Ready).invite.code != code)
    }

    @Test
    fun cancel_offline_keepsTheCode_andSaysSo() {
        viewModel.onInvite()
        val ready = state.code
        invites.offline = true

        viewModel.onCancelCode()

        assertEquals(ready, state.code)
        assertEquals(InviteMessage.CancelFailed, state.message)
    }

    @Test
    fun aFullTrip_getsNoCode() {
        trips.trips.value = listOf("u1" to rome.copy(memberIds = listOf("u1") + List(9) { "m$it" }))
        viewModel.onInvite()
        assertEquals(InviteCodeState.Full, state.code)
        assertTrue(invites.created.isEmpty())

        trips.trips.value = listOf("u1" to rome)
        invites.full = true
        viewModel.onRetry()
        assertEquals(InviteCodeState.Full, state.code)
    }

    @Test
    fun offline_showsTryAgain() {
        invites.offline = true
        viewModel.onInvite()
        assertEquals(InviteCodeState.Failed, state.code)

        invites.offline = false
        viewModel.onRetry()
        assertTrue(state.code is InviteCodeState.Ready)
    }

    @Test
    fun aMemberWhoIsNotTheOwner_neverGetsACode() {
        trips.trips.value = listOf("u1" to rome.copy(ownerId = "noa", memberIds = listOf("noa", "u1")))
        viewModel.onInvite()
        assertEquals(InviteCodeState.Failed, state.code)
        assertTrue(invites.created.isEmpty())
    }

    @Test
    fun removingAMember_asksFirst_thenRemovesOnce() {
        val gate = CompletableDeferred<Unit>()
        sync.removeGate = gate

        viewModel.onRemoveMember(noa)
        assertEquals(noa, state.removing)
        assertTrue(sync.removeCalls.isEmpty())

        viewModel.onConfirmRemove()
        viewModel.onConfirmRemove()
        viewModel.onDismissRemove() // ignored while it runs
        gate.complete(Unit)

        assertEquals(listOf("t1" to "noa"), sync.removeCalls)
        assertNull(state.removing)
        assertEquals(InviteMessage.Removed("Noa"), state.message)
    }

    @Test
    fun removing_failed_saysSo_andCancelRemovesNothing() {
        viewModel.onRemoveMember(noa)
        viewModel.onDismissRemove()
        assertNull(state.removing)
        assertTrue(sync.removeCalls.isEmpty())

        sync.removeResult = false
        viewModel.onRemoveMember(noa)
        viewModel.onConfirmRemove()
        assertEquals(InviteMessage.RemoveFailed, state.message)
    }

    @Test
    fun youAreNeverOfferedForRemoval() {
        viewModel.onRemoveMember(MemberEntry(Person.You("u1"), isOwner = true, joinedOn = null))
        assertNull(state.removing)
    }
}
