package com.example.planyourtrip.ui.tripdetails.activity

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
import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.ActivityRepository
import com.example.planyourtrip.data.trip.DeletedActivityStore
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.TextLimits
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.data.trip.limitTo
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.common.SavedFormState
import com.example.planyourtrip.ui.navigation.ActivityForm
import com.example.planyourtrip.ui.tripdetails.defaultDay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.Serializable
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
    /** Receives the deleted activity, so the trip screen can offer "Undo". */
    private val deletedActivities: DeletedActivityStore = DeletedActivityStore(),
    /** Keeps the form's draft across process death. */
    savedState: SavedStateHandle? = null,
) : ViewModel() {

    private val form = SavedFormState(ActivityFormUiState(isEditing = activityId != null), savedState, KEY_DRAFT) {
        if (it.isLoading) null else ActivityDraft.of(it)
    }
    val uiState: StateFlow<ActivityFormUiState> = form.flow

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
                form.update { it.copy(isLoading = false, done = true) }
                return@launch
            }
            val activity = activityId?.let { activityRepository.observeActivity(it, ownerId).first() }
            val restored = form.restored
            form.update { state ->
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
                }.let { loaded ->
                    // After process death the form comes back as the user left it.
                    restored?.applyTo(loaded) ?: loaded
                }
            }
        }
    }

    fun onTitleChange(value: String) =
        form.update { it.copy(title = value.limitTo(TextLimits.ACTIVITY_TITLE), titleError = null) }

    fun onTypeChange(type: ActivityType) = form.update { it.copy(type = type) }

    fun onDateChange(date: LocalDate) = form.update { it.copy(date = date) }

    /** Null clears the start time. */
    fun onStartTimeChange(time: LocalTime?) = form.update { it.copy(startTime = time) }

    fun onLocationChange(value: String) =
        form.update { it.copy(location = value.limitTo(TextLimits.ACTIVITY_LOCATION)) }

    fun onCostChange(value: String) = form.update { it.copy(cost = value, costError = null) }

    fun onBookedChange(booked: Boolean) = form.update { it.copy(booked = booked) }

    fun onNoteChange(value: String) = form.update { it.copy(note = value.limitTo(TextLimits.ACTIVITY_NOTE)) }

    fun onSave() {
        val state = form.value
        if (state.isBusy) return
        val date = state.date ?: return

        val validated = state.copy(
            titleError = ActivityValidator.validateTitle(state.title),
            costError = ActivityValidator.validateCost(state.cost),
        )
        form.update { validated }
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
        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            if (activityId == null) {
                activityRepository.addActivity(tripId, ownerId, input)
            } else {
                activityRepository.updateActivity(activityId, ownerId, input)
            }
            form.update { it.copy(isSaving = false, done = true) }
        }
    }

    /**
     * Deletes the edited activity. The confirmation dialog is shown by the screen before this is called; the trip
     * screen then offers "Undo".
     */
    fun onDelete() {
        val id = activityId ?: return
        if (form.value.isBusy) return
        val ownerId = authRepository.currentUser.value?.uid ?: return
        form.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            activityRepository.deleteActivity(id, ownerId)?.let(deletedActivities::offer)
            form.update { it.copy(isSaving = false, done = true) }
        }
    }

    private val ActivityFormUiState.isBusy: Boolean get() = isLoading || isSaving || done

    companion object {
        private const val KEY_DRAFT = "activityDraft"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                val route = handle.toRoute<ActivityForm>()
                val container = appContainer()
                ActivityFormViewModel(
                    tripId = route.tripId,
                    activityId = route.activityId,
                    initialDate = route.dateEpochDay?.let(LocalDate::ofEpochDay),
                    activityRepository = container.activityRepository,
                    tripRepository = container.tripRepository,
                    authRepository = container.authRepository,
                    clock = container.clock,
                    deletedActivities = container.deletedActivities,
                    savedState = handle,
                )
            }
        }
    }
}

/** What the user entered and chose in the activity form, kept across process death (see [SavedFormState]). */
private data class ActivityDraft(
    val title: String,
    val type: ActivityType,
    val date: LocalDate?,
    val startTime: LocalTime?,
    val location: String,
    val cost: String,
    val booked: Boolean,
    val note: String,
) : Serializable {
    fun applyTo(state: ActivityFormUiState) = state.copy(
        title = title,
        type = type,
        date = date ?: state.date,
        startTime = startTime,
        location = location,
        cost = cost,
        booked = booked,
        note = note,
    )

    companion object {
        fun of(state: ActivityFormUiState) = ActivityDraft(
            title = state.title,
            type = state.type,
            date = state.date,
            startTime = state.startTime,
            location = state.location,
            cost = state.cost,
            booked = state.booked,
            note = state.note,
        )
    }
}
