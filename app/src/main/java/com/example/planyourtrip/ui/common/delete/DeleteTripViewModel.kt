package com.example.planyourtrip.ui.common.delete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.document.DocumentRepository
import com.example.planyourtrip.data.sync.SyncController
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * "Delete trip": the confirmation dialog and the delete itself (the trip with its expenses, activities and checklist
 * items, recorded for the sync, then its document files). Home deletes right away ([onConfirm]); the trip screen only
 * confirms ([takeConfirmed]) and hands the id to Home, which deletes it with [deleteConfirmed].
 *
 * A shared trip I don't own can't be deleted: [onRemoveTrip] opens "Leave this trip?" instead, and confirming leaves
 * it ([SyncController.leaveTrip]: online only; the trip and its documents are then removed from this device). The
 * trip screen hands a confirmed leave to Home too ([leaveConfirmed]).
 */
class DeleteTripViewModel(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val documentRepository: DocumentRepository,
    /** Leaves shared trips; null = leaving always fails (tests of deleting only). */
    private val syncController: SyncController? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteTripUiState())
    val uiState: StateFlow<DeleteTripUiState> = _uiState.asStateFlow()

    /** Opens the dialog for [tripId]; ignored while a delete runs. */
    fun onDeleteTrip(tripId: Long, destination: String) {
        if (_uiState.value.dialog?.isDeleting == true) return
        _uiState.update { it.copy(dialog = DeleteDialogState(tripId, destination)) }
    }

    /**
     * The menu's "Delete trip" / "Leave trip" or a swipe: deletes my own trip, leaves a shared trip someone else
     * owns (a trip without a known owner counts as mine).
     */
    fun onRemoveTrip(trip: Trip) {
        if (_uiState.value.dialog?.isDeleting == true) return
        _uiState.update { it.copy(dialog = DeleteDialogState(trip.id, trip.destination, leave = !isMine(trip))) }
    }

    /** Cancel; ignored while the delete runs. */
    fun onDismiss() {
        if (_uiState.value.dialog?.isDeleting == true) return
        _uiState.update { it.copy(dialog = null) }
    }

    /** "Delete" on Home. `isDeleting` is set synchronously, so a double tap deletes once. */
    fun onConfirm() {
        val dialog = _uiState.value.dialog ?: return
        if (dialog.isDeleting) return
        _uiState.update { it.copy(dialog = dialog.copy(isDeleting = true)) }
        viewModelScope.launch {
            val message = if (dialog.leave) leave(dialog.tripId).toLeaveMessage() else delete(dialog.tripId).toMessage()
            _uiState.update { it.copy(dialog = null, message = message) }
        }
    }

    /** "Delete" on the trip screen: closes the dialog and returns the trip to delete on Home (null = no dialog). */
    fun takeConfirmed(): Long? {
        val dialog = _uiState.value.dialog ?: return null
        if (dialog.isDeleting) return null
        _uiState.update { it.copy(dialog = null) }
        return dialog.tripId
    }

    /** Deletes a trip the user already confirmed on the trip screen. */
    fun deleteConfirmed(tripId: Long) {
        viewModelScope.launch {
            val deleted = delete(tripId)
            _uiState.update { it.copy(message = deleted.toMessage()) }
        }
    }

    /** Leaves a trip the user already confirmed leaving on the trip screen. */
    fun leaveConfirmed(tripId: Long) {
        viewModelScope.launch {
            val left = leave(tripId)
            _uiState.update { it.copy(message = left.toLeaveMessage()) }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    /** False when there is no user, the trip isn't theirs (any more) or the delete failed. */
    private suspend fun delete(tripId: Long): Boolean {
        val uid = authRepository.currentUser.value?.uid ?: return false
        val tripUuid = try {
            tripRepository.deleteTrip(tripId, uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return false
        try {
            documentRepository.deleteForTrip(tripUuid, uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The trip is gone; the next full sync's orphan cleanup removes what is left of its documents.
        }
        return true
    }

    /** False when there is no user, the trip isn't on this device, it's my own, or leaving failed (offline). */
    private suspend fun leave(tripId: Long): Boolean {
        val sync = syncController ?: return false
        val uid = authRepository.currentUser.value?.uid ?: return false
        val trip = tripRepository.observeTrip(tripId, uid).first() ?: return false
        if (isMine(trip) || trip.uuid.isBlank()) return false
        return try {
            sync.leaveTrip(trip.uuid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private fun isMine(trip: Trip): Boolean {
        val uid = authRepository.currentUser.value?.uid
        return trip.ownerId.isBlank() || trip.ownerId == uid
    }

    private fun Boolean.toMessage() = if (this) DeleteTripMessage.DELETED else DeleteTripMessage.FAILED

    private fun Boolean.toLeaveMessage() = if (this) DeleteTripMessage.LEFT else DeleteTripMessage.LEAVE_FAILED

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                DeleteTripViewModel(
                    container.authRepository,
                    container.tripRepository,
                    container.documentRepository,
                    container.syncController,
                )
            }
        }
    }
}
