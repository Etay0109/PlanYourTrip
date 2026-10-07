package com.example.planyourtrip.ui.common.delete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.document.DocumentRepository
import com.example.planyourtrip.data.trip.TripRepository
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
 */
class DeleteTripViewModel(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteTripUiState())
    val uiState: StateFlow<DeleteTripUiState> = _uiState.asStateFlow()

    /** Opens the dialog for [tripId]; ignored while a delete runs. */
    fun onDeleteTrip(tripId: Long, destination: String) {
        if (_uiState.value.dialog?.isDeleting == true) return
        _uiState.update { it.copy(dialog = DeleteDialogState(tripId, destination)) }
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
            val deleted = delete(dialog.tripId)
            _uiState.update { it.copy(dialog = null, message = deleted.toMessage()) }
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

    private fun Boolean.toMessage() = if (this) DeleteTripMessage.DELETED else DeleteTripMessage.FAILED

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                DeleteTripViewModel(container.authRepository, container.tripRepository, container.documentRepository)
            }
        }
    }
}
