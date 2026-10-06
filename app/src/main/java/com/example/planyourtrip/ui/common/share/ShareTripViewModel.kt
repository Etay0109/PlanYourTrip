package com.example.planyourtrip.ui.common.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.trip.ActivityRepository
import com.example.planyourtrip.data.trip.ExpenseRepository
import com.example.planyourtrip.data.trip.TripRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * "Share trip": the include-costs dialog, creating the PDF and handing it to the share sheet. Used by Home (any
 * trip card) and the trip screen; screens only see this ViewModel and [ShareTripHost], never the PDF code.
 */
class ShareTripViewModel(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository,
    private val activityRepository: ActivityRepository,
    private val exporter: TripPdfExporter,
    /** Where the PDF content is built; tests pass their test dispatcher. */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShareTripUiState())
    val uiState: StateFlow<ShareTripUiState> = _uiState.asStateFlow()

    /** Opens the dialog for [tripId], with costs included. */
    fun onShareTrip(tripId: Long) {
        if (_uiState.value.dialog?.isCreating == true) return
        _uiState.update { it.copy(dialog = ShareDialogState(tripId)) }
    }

    fun onIncludeCostsChange(include: Boolean) = updateDialog { if (it.isCreating) it else it.copy(includeCosts = include) }

    /** Ignored while the PDF is being created. */
    fun onDismiss() {
        if (_uiState.value.dialog?.isCreating == true) return
        _uiState.update { it.copy(dialog = null) }
    }

    /** "Create PDF" and "Try again". `isCreating` is set synchronously, so a double tap creates one PDF. */
    fun onCreatePdf() {
        val dialog = _uiState.value.dialog ?: return
        if (dialog.isCreating) return
        updateDialog { it.copy(isCreating = true, failed = false) }
        viewModelScope.launch {
            val shared = try {
                create(dialog)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The exporter logs the cause.
                null
            }
            _uiState.update { state ->
                if (shared != null) state.copy(dialog = null, pendingShare = shared)
                else state.copy(dialog = state.dialog?.copy(isCreating = false, failed = true))
            }
        }
    }

    /** The share sheet was opened for [ShareTripUiState.pendingShare]. */
    fun onShareLaunched() {
        _uiState.update { it.copy(pendingShare = null) }
    }

    /** Null when there is no user or the trip isn't theirs (any more). */
    private suspend fun create(dialog: ShareDialogState): SharedPdf? {
        val uid = authRepository.currentUser.value?.uid ?: return null
        val trip = tripRepository.observeTrip(dialog.tripId, uid).first() ?: return null
        val expenses = if (dialog.includeCosts) expenseRepository.observeExpenses(trip.id, uid).first() else emptyList()
        val activities = activityRepository.observeActivities(trip.id, uid).first()
        val content = withContext(computeDispatcher) {
            buildTripPdfContent(trip, expenses, activities, dialog.includeCosts)
        }
        return exporter.export(content, pdfFileName(trip.destination, trip.startDate, trip.endDate))
    }

    private fun updateDialog(change: (ShareDialogState) -> ShareDialogState) =
        _uiState.update { state -> state.copy(dialog = state.dialog?.let(change)) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ShareTripViewModel(
                    container.authRepository,
                    container.tripRepository,
                    container.expenseRepository,
                    container.activityRepository,
                    container.tripPdfExporter,
                )
            }
        }
    }
}
