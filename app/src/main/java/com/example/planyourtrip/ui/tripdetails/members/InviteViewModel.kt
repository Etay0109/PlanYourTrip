package com.example.planyourtrip.ui.tripdetails.members

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
import com.example.planyourtrip.data.sharing.Invite
import com.example.planyourtrip.data.sharing.InviteRepository
import com.example.planyourtrip.data.sharing.InviteUnavailableException
import com.example.planyourtrip.data.sharing.TripFullException
import com.example.planyourtrip.data.sharing.memberName
import com.example.planyourtrip.data.sync.SyncController
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.navigation.TripDetails
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The members sheet of one trip: the owner's invite code (shown again while it is open, otherwise a new one is made;
 * cancel), and removing a member (owner, after a confirmation). Leaving is the trip menu's "Leave trip"
 * (DeleteTripViewModel). Every condition is enforced by firestore.rules; this only words the outcome.
 */
class InviteViewModel(
    private val tripId: Long,
    private val tripRepository: TripRepository,
    private val authRepository: AuthRepository,
    private val inviteRepository: InviteRepository,
    private val syncController: SyncController,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InviteUiState())
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

    private var codeJob: Job? = null

    /** The "Traveling together" card: the members list only. */
    fun onShowMembers() = _uiState.update { it.copy(sheet = MembersSheetMode.MEMBERS) }

    /** "Invite" (owner only): the open code, or a new one. */
    fun onInvite() {
        _uiState.update { it.copy(sheet = MembersSheetMode.INVITE, code = InviteCodeState.Loading) }
        loadCode(createNew = false)
    }

    /** "Try again" after a failure. */
    fun onRetry() {
        _uiState.update { it.copy(code = InviteCodeState.Loading) }
        loadCode(createNew = false)
    }

    /** "Create a new code" after cancelling. */
    fun onNewCode() {
        _uiState.update { it.copy(code = InviteCodeState.Loading) }
        loadCode(createNew = true)
    }

    fun onDismissSheet() {
        codeJob?.cancel()
        _uiState.update { it.copy(sheet = null, code = InviteCodeState.Loading) }
    }

    /** Shows the open invite again, or creates one (also when the last one was used or expired). */
    private fun loadCode(createNew: Boolean) {
        codeJob?.cancel()
        codeJob = viewModelScope.launch {
            val state = try {
                val (user, trip) = ownTrip() ?: throw InviteUnavailableException()
                if (trip.memberIds.size >= MembersState.MAX_MEMBERS) {
                    InviteCodeState.Full
                } else {
                    val open = if (createNew) null else {
                        inviteRepository.currentInvite(user, trip.uuid)?.takeIf { it.isOpenAt(clock.millis()) }
                    }
                    ready(open ?: inviteRepository.createInvite(user, trip.uuid, ownerName()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TripFullException) {
                InviteCodeState.Full
            } catch (e: InviteUnavailableException) {
                InviteCodeState.Failed
            }
            _uiState.update { it.copy(code = state) }
        }
    }

    /** "Cancel this code": it stops working at once. */
    fun onCancelCode() {
        val ready = _uiState.value.code as? InviteCodeState.Ready ?: return
        if (ready.isCancelling) return
        _uiState.update { it.copy(code = ready.copy(isCancelling = true)) }
        viewModelScope.launch {
            val cancelled = try {
                val (user, trip) = ownTrip() ?: throw InviteUnavailableException()
                inviteRepository.cancelInvite(user, trip.uuid, ready.invite.code)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: InviteUnavailableException) {
                false
            }
            _uiState.update {
                if (cancelled) {
                    it.copy(code = InviteCodeState.Cancelled)
                } else {
                    it.copy(code = ready, message = InviteMessage.CancelFailed)
                }
            }
        }
    }

    /** A member's ⋮ → "Remove from trip": asks first. Only for the owner, never for themselves. */
    fun onRemoveMember(member: MemberEntry) {
        if (member.person is Person.You || _uiState.value.isRemoving) return
        _uiState.update { it.copy(removing = member) }
    }

    fun onDismissRemove() {
        if (_uiState.value.isRemoving) return
        _uiState.update { it.copy(removing = null) }
    }

    /** "Remove". `isRemoving` is set synchronously, so a double tap removes once. */
    fun onConfirmRemove() {
        val member = _uiState.value.removing ?: return
        if (_uiState.value.isRemoving) return
        _uiState.update { it.copy(isRemoving = true) }
        viewModelScope.launch {
            val removed = try {
                val (_, trip) = ownTrip() ?: throw InviteUnavailableException()
                syncController.removeMember(trip.uuid, member.uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            val name = (member.person as? Person.Member)?.name
            _uiState.update {
                it.copy(
                    removing = null,
                    isRemoving = false,
                    message = if (removed) InviteMessage.Removed(name) else InviteMessage.RemoveFailed,
                )
            }
        }
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    /** The signed-in user's uid and their trip, only when they are its owner. */
    private suspend fun ownTrip(): Pair<String, Trip>? {
        val uid = authRepository.currentUser.value?.uid ?: return null
        val trip = tripRepository.observeTrip(tripId, uid).first() ?: return null
        if (trip.uuid.isBlank() || (trip.ownerId.isNotBlank() && trip.ownerId != uid)) return null
        return uid to trip
    }

    private fun ownerName(): String = authRepository.currentUser.value?.let(::memberName).orEmpty()

    private fun ready(invite: Invite) = InviteCodeState.Ready(
        invite = invite,
        expiresOn = Instant.ofEpochMilli(invite.expiresAt).atZone(clock.zone).toLocalDate(),
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                val container = appContainer()
                InviteViewModel(
                    tripId = handle.toRoute<TripDetails>().tripId,
                    tripRepository = container.tripRepository,
                    authRepository = container.authRepository,
                    inviteRepository = container.inviteRepository,
                    syncController = container.syncController,
                    clock = container.clock,
                )
            }
        }
    }
}
