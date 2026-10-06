package com.example.planyourtrip.ui.tripdetails.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.ActivityRepository
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.navigation.ActivityForm
import com.example.planyourtrip.ui.tripdetails.defaultDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * New activity of [tripId] when [activityId] is null (on [initialDate], or the trip's default day); otherwise
 * edits (or deletes) that activity.
 */
class ActivityFormViewModel(
    private val tripId: Long,
    private val activityId: Long?,
    private val initialDate: LocalDate?,
    private val activityRepository: ActivityRepository,
    private val tripRepository: TripRepository,
    private val authRepository: AuthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityFormUiState(isEditing = activityId != null))
    val uiState: StateFlow<ActivityFormUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Reads the trip dates (picker range, default day) and, when editing, fills the fields once. */
    private fun load() {
        val ownerId = authRepository.currentUser.value?.uid ?: return
        viewModelScope.launch {
            val trip = tripRepository.observeTrip(tripId, ownerId).first()
            if (trip == null) {
                // Not the user's trip, or deleted meanwhile: nothing to edit.
                _uiState.update { it.copy(isLoading = false, done = true) }
                return@launch
            }
            val activity = activityId?.let { activityRepository.observeActivity(it, ownerId).first() }
            _uiState.update { state ->
                val base = state.copy(
                    isLoading = false,
                    tripStart = trip.startDate,
                    tripEnd = trip.endDate,
                    date = initialDate ?: defaultDay(trip.startDate, trip.endDate, LocalDate.now(clock)),
                )
                if (activity == null) {
                    base
                } else {
                    base.copy(
                        title = activity.title,
                        type = activity.type,
                        date = activity.date,
                        startTime = activity.startTime,
                        location = activity.location.orEmpty(),
                        cost = activity.estimatedCostAgorot?.let(Money::toInput).orEmpty(),
                        booked = activity.booked,
                        note = activity.note.orEmpty(),
                    )
                }
            }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, titleError = null) }

    fun onTypeChange(type: ActivityType) = _uiState.update { it.copy(type = type) }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(date = date) }

    /** Null clears the start time. */
    fun onStartTimeChange(time: LocalTime?) = _uiState.update { it.copy(startTime = time) }

    fun onLocationChange(value: String) = _uiState.update { it.copy(location = value) }

    fun onCostChange(value: String) = _uiState.update { it.copy(cost = value, costError = null) }

    fun onBookedChange(booked: Boolean) = _uiState.update { it.copy(booked = booked) }

    fun onNoteChange(value: String) = _uiState.update { it.copy(note = value) }

    fun onSave() {
        val state = _uiState.value
        if (state.isBusy) return
        val date = state.date ?: return

        val validated = state.copy(
            titleError = ActivityValidator.validateTitle(state.title),
            costError = ActivityValidator.validateCost(state.cost),
        )
        _uiState.value = validated
        if (validated.titleError != null || validated.costError != null) return

        val ownerId = authRepository.currentUser.value?.uid ?: return
        val input = ActivityInput(
            date = date,
            startTime = validated.startTime,
            title = validated.title,
            type = validated.type,
            note = validated.note,
            location = validated.location,
            estimatedCostAgorot = validated.cost.takeIf { it.isNotBlank() }?.let(Money::parseAgorot),
            booked = validated.booked,
        )
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            if (activityId == null) {
                activityRepository.addActivity(tripId, ownerId, input)
            } else {
                activityRepository.updateActivity(activityId, ownerId, input)
            }
            _uiState.update { it.copy(isSaving = false, done = true) }
        }
    }

    /** Deletes the edited activity. The confirmation dialog is shown by the screen before this is called. */
    fun onDelete() {
        val id = activityId ?: return
        if (_uiState.value.isBusy) return
        val ownerId = authRepository.currentUser.value?.uid ?: return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            activityRepository.deleteActivity(id, ownerId)
            _uiState.update { it.copy(isSaving = false, done = true) }
        }
    }

    private val ActivityFormUiState.isBusy: Boolean get() = isLoading || isSaving || done

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val route = createSavedStateHandle().toRoute<ActivityForm>()
                val container = appContainer()
                ActivityFormViewModel(
                    tripId = route.tripId,
                    activityId = route.activityId,
                    initialDate = route.dateEpochDay?.let(LocalDate::ofEpochDay),
                    activityRepository = container.activityRepository,
                    tripRepository = container.tripRepository,
                    authRepository = container.authRepository,
                    clock = container.clock,
                )
            }
        }
    }
}
