package com.example.planyourtrip.ui.tripform

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
import com.example.planyourtrip.data.currency.Currency
import com.example.planyourtrip.data.currency.CurrencyRepository
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.common.CurrencyPickerState
import com.example.planyourtrip.ui.common.currencyName
import com.example.planyourtrip.ui.navigation.TripForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** New trip when [tripId] is null; otherwise edits destination, dates and currency of that trip. */
class TripFormViewModel(
    private val tripId: Long?,
    private val repository: TripRepository,
    private val authRepository: AuthRepository,
    /** Only for the list of currencies in the picker. */
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {

    /** The service's currencies, once loaded (used for names). */
    private var currencies: List<Currency> = emptyList()

    private val _uiState = MutableStateFlow(TripFormUiState(isEditing = tripId != null, isLoading = tripId != null))
    val uiState: StateFlow<TripFormUiState> = _uiState.asStateFlow()

    init {
        if (tripId != null) loadTrip(tripId)
    }

    /** Fills the fields once with the trip's current values (only the owner's trips). */
    private fun loadTrip(id: Long) {
        val ownerId = authRepository.currentUser.value?.uid ?: return
        viewModelScope.launch {
            val trip = repository.observeTrip(id, ownerId).first()
            _uiState.update {
                if (trip == null) it.copy(isLoading = false)
                else it.copy(
                    isLoading = false,
                    destination = trip.destination,
                    startDate = trip.startDate,
                    endDate = trip.endDate,
                    currencyCode = trip.currencyCode,
                    currencyName = trip.currencyCode?.let { code -> currencyName(code, currencies) },
                )
            }
        }
    }

    fun onDestinationChange(value: String) =
        _uiState.update { it.copy(destination = value, destinationError = null) }

    // Changing either date clears both date errors, since END_BEFORE_START depends on both.
    fun onStartDateChange(date: LocalDate) =
        _uiState.update { it.copy(startDate = date, startError = null, endError = null) }

    fun onEndDateChange(date: LocalDate) =
        _uiState.update { it.copy(endDate = date, startError = null, endError = null) }

    /** Opens the searchable currency list (nothing preselected for a new trip). */
    fun onChooseCurrency() {
        _uiState.update { it.copy(currencyPicker = CurrencyPickerState(selectedCode = it.currencyCode)) }
        loadCurrencies()
    }

    fun onRetryCurrencies() = loadCurrencies()

    private fun loadCurrencies() {
        _uiState.update { it.copy(currencyPicker = it.currencyPicker?.loading()) }
        viewModelScope.launch {
            val list = currencyRepository.currencies()
            if (list != null) currencies = list
            _uiState.update { it.copy(currencyPicker = it.currencyPicker?.loaded(list)) }
        }
    }

    fun onCurrencyQueryChange(query: String) =
        _uiState.update { it.copy(currencyPicker = it.currencyPicker?.withQuery(query)) }

    fun onDismissCurrencyPicker() = _uiState.update { it.copy(currencyPicker = null) }

    fun onCurrencySelected(code: String) = _uiState.update {
        it.copy(currencyCode = code, currencyName = currencyName(code, currencies), currencyPicker = null)
    }

    /** The currency is optional: the clear icon removes it. */
    fun onClearCurrency() = _uiState.update { it.copy(currencyCode = null, currencyName = null) }

    fun onSave() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.saved) return

        val validated = state.copy(
            destinationError = TripValidator.validateDestination(state.destination),
            startError = TripValidator.validateStart(state.startDate),
            endError = TripValidator.validateEnd(state.startDate, state.endDate),
        )
        _uiState.value = validated
        if (validated.destinationError != null || validated.startError != null || validated.endError != null) return

        // Behind Login there is always a user; without one there is nobody to own the trip.
        val ownerId = authRepository.currentUser.value?.uid ?: return
        val destination = validated.destination.trim()
        val start = checkNotNull(validated.startDate)
        val end = checkNotNull(validated.endDate)
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val currency = validated.currencyCode
            if (tripId == null) repository.addTrip(ownerId, destination, start, end, currency)
            else repository.updateTrip(tripId, ownerId, destination, start, end, currency)
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                val container = appContainer()
                TripFormViewModel(
                    tripId = handle.toRoute<TripForm>().tripId,
                    repository = container.tripRepository,
                    authRepository = container.authRepository,
                    currencyRepository = container.currencyRepository,
                )
            }
        }
    }
}
