package com.example.planyourtrip.ui.home.join

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.sharing.InviteCode
import com.example.planyourtrip.data.sharing.InviteRepository
import com.example.planyourtrip.data.sharing.InviteUnavailableException
import com.example.planyourtrip.data.sharing.JoinResult
import com.example.planyourtrip.data.sharing.memberName
import com.example.planyourtrip.data.sync.SyncController
import com.example.planyourtrip.data.trip.TripRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Home's "Join trip": a complete code is looked up ([InviteRepository.lookUp]), its trip shown, and only then can
 * the user join. After joining, a full sync brings the trip to this device. The cloud's rules decide everything;
 * this only words the outcome (one message for wrong, expired, used and cancelled codes).
 */
class JoinTripViewModel(
    private val authRepository: AuthRepository,
    private val inviteRepository: InviteRepository,
    private val tripRepository: TripRepository,
    private val syncController: SyncController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(JoinUiState())
    val uiState: StateFlow<JoinUiState> = _uiState.asStateFlow()

    private var lookupJob: Job? = null

    fun onOpen() {
        lookupJob?.cancel()
        _uiState.update { JoinUiState(isOpen = true, joinedDestination = it.joinedDestination) }
    }

    /** Cancel / swipe down; ignored while joining. */
    fun onDismiss() {
        if (_uiState.value.isJoining) return
        lookupJob?.cancel()
        _uiState.update { JoinUiState(joinedDestination = it.joinedDestination) }
    }

    /** The typed code changed ([text] is already normalized by the field). A complete code is looked up. */
    fun onCodeChange(text: String) {
        val code = InviteCode.normalize(text)
        val state = _uiState.value
        if (state.isJoining) return
        val complete = InviteCode.isValid(code)
        if (complete && code == state.code && state.lookup !is CodeLookup.Incomplete) return
        lookupJob?.cancel()
        if (!complete) {
            _uiState.update { it.copy(code = "", lookup = CodeLookup.Incomplete, error = null) }
            return
        }
        lookUp(code)
    }

    /** "Try again" after a failed lookup. */
    fun onRetry() {
        val code = _uiState.value.code
        if (InviteCode.isValid(code) && !_uiState.value.isJoining) lookUp(code)
    }

    private fun lookUp(code: String) {
        _uiState.update { it.copy(code = code, lookup = CodeLookup.Checking, error = null) }
        val uid = authRepository.currentUser.value?.uid ?: return
        lookupJob = viewModelScope.launch {
            val result = try {
                inviteRepository.lookUp(uid, code)?.let { CodeLookup.Found(it) } ?: CodeLookup.Invalid
            } catch (e: CancellationException) {
                throw e
            } catch (e: InviteUnavailableException) {
                CodeLookup.Failed
            }
            _uiState.update { if (it.code == code) it.copy(lookup = result) else it }
        }
    }

    /** "Join trip". `isJoining` is set synchronously, so a double tap joins once. */
    fun onJoin() {
        val state = _uiState.value
        val preview = (state.lookup as? CodeLookup.Found)?.preview ?: return
        if (state.isJoining) return
        val user = authRepository.currentUser.value ?: return
        _uiState.update { it.copy(isJoining = true, error = null) }
        viewModelScope.launch {
            // Already on this device for this account: no need to ask the cloud.
            val mine = tripRepository.observeTrips(user.uid).first().any { it.uuid == preview.tripUuid }
            val result = if (mine) JoinResult.ALREADY_MEMBER else inviteRepository.join(user.uid, preview, memberName(user))
            when (result) {
                JoinResult.JOINED -> {
                    _uiState.value = JoinUiState(joinedDestination = preview.destination)
                    // The trip, its items and members come down with a full sync.
                    syncController.refresh()
                }
                JoinResult.INVALID_CODE -> _uiState.update {
                    it.copy(isJoining = false, lookup = CodeLookup.Invalid, error = JoinError.INVALID_CODE)
                }
                JoinResult.ALREADY_MEMBER -> _uiState.update { it.copy(isJoining = false, error = JoinError.ALREADY_MEMBER) }
                JoinResult.TRIP_FULL -> _uiState.update { it.copy(isJoining = false, error = JoinError.TRIP_FULL) }
                JoinResult.FAILED -> _uiState.update { it.copy(isJoining = false, error = JoinError.FAILED) }
            }
        }
    }

    fun onJoinedShown() = _uiState.update { it.copy(joinedDestination = null) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                JoinTripViewModel(
                    authRepository = container.authRepository,
                    inviteRepository = container.inviteRepository,
                    tripRepository = container.tripRepository,
                    syncController = container.syncController,
                )
            }
        }
    }
}
