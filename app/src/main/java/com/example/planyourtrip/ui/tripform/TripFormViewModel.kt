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
import com.example.planyourtrip.data.place.DestinationSuggestions
import com.example.planyourtrip.data.place.PlaceSuggestion
import com.example.planyourtrip.data.place.countryCurrency
import com.example.planyourtrip.data.trip.TripPlace
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.data.trip.place
import com.example.planyourtrip.ui.common.CurrencyPickerState
import com.example.planyourtrip.ui.common.currencyName
import com.example.planyourtrip.ui.navigation.TripForm
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** New trip when [tripId] is null; otherwise edits destination, dates, currency and place of that trip. */
class TripFormViewModel(
    private val tripId: Long?,
    private val repository: TripRepository,
    private val authRepository: AuthRepository,
    /** For the list of currencies in the picker (and which currency a picked place may preselect). */
    private val currencyRepository: CurrencyRepository,
    /** Places for the destination field (Photon + the Hebrew table). */
    private val destinations: DestinationSuggestions,
) : ViewModel() {

    /** The running destination lookup; a new keystroke cancels it (and its request). */
    private var lookup: Job? = null
    private var preselection: Job? = null

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
                    // Kept while the text stays as it is.
                    place = trip.place.takeIf { p -> p != TripPlace.NONE },
                    startDate = trip.startDate,
                    endDate = trip.endDate,
                    currencyCode = trip.currencyCode,
                    currencyName = trip.currencyCode?.let { code -> currencyName(code, currencies) },
                )
            }
        }
    }

    /**
     * Typing: any change of the text forgets the picked place (until a suggestion is picked again) and looks the text
     * up after a short pause, from [DestinationSuggestions.MIN_QUERY_LENGTH] characters.
     */
    fun onDestinationChange(value: String) {
        val changed = value != _uiState.value.destination
        _uiState.update {
            it.copy(destination = value, destinationError = null, place = if (changed) null else it.place)
        }
        if (!changed) return
        lookup?.cancel()
        if (value.trim().length < DestinationSuggestions.MIN_QUERY_LENGTH) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        lookup = viewModelScope.launch {
            delay(LOOKUP_DELAY_MILLIS)
            val found = destinations.suggest(value)
            // A slow answer for older text is never shown.
            _uiState.update { if (it.destination == value) it.copy(suggestions = found) else it }
        }
    }

    /** Picking fills "London, United Kingdom", remembers the country and coordinates, and may preselect a currency. */
    fun onSuggestionPicked(suggestion: PlaceSuggestion) {
        lookup?.cancel()
        _uiState.update {
            it.copy(
                destination = suggestion.label,
                destinationError = null,
                suggestions = emptyList(),
                place = suggestion.toTripPlace(),
            )
        }
        preselectCurrency(suggestion.countryCode)
    }

    /** The field lost focus: the list closes (the text and a picked place stay). */
    fun onSuggestionsDismissed() {
        lookup?.cancel()
        _uiState.update { it.copy(suggestions = emptyList()) }
    }

    /**
     * A new trip without a currency chosen by the user gets the picked country's currency, if the app offers it;
     * a currency preselected for an earlier pick follows the new pick. Editing a trip never changes its currency.
     */
    private fun preselectCurrency(countryCode: String?) {
        val state = _uiState.value
        if (state.isEditing || (state.currencyCode != null && !state.currencyPreselected)) return
        preselection?.cancel()
        preselection = viewModelScope.launch {
            val code = countryCode?.let(::countryCurrency)
            val list = if (code != null) currencyRepository.currencies() else null
            if (list != null) currencies = list
            val offered = code != null && list.orEmpty().any { it.code == code }
            _uiState.update {
                when {
                    // The user chose a currency in the meantime.
                    it.currencyCode != null && !it.currencyPreselected -> it
                    offered -> it.copy(
                        currencyCode = code,
                        currencyName = currencyName(checkNotNull(code), currencies),
                        currencyPreselected = true,
                    )
                    it.currencyPreselected -> it.copy(currencyCode = null, currencyName = null, currencyPreselected = false)
                    else -> it
                }
            }
        }
    }

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

    fun onCurrencySelected(code: String) {
        preselection?.cancel()
        _uiState.update {
            it.copy(
                currencyCode = code, currencyName = currencyName(code, currencies), currencyPicker = null,
                currencyPreselected = false,
            )
        }
    }

    /** The currency is optional: the clear icon removes it. */
    fun onClearCurrency() {
        preselection?.cancel()
        _uiState.update { it.copy(currencyCode = null, currencyName = null, currencyPreselected = false) }
    }

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
        // Free text (nothing picked, or edited since) is saved without a country or coordinates.
        val place = validated.place ?: TripPlace.NONE
        lookup?.cancel()
        _uiState.update { it.copy(isSaving = true, suggestions = emptyList()) }
        viewModelScope.launch {
            val currency = validated.currencyCode
            if (tripId == null) repository.addTrip(ownerId, destination, start, end, currency, place)
            else repository.updateTrip(tripId, ownerId, destination, start, end, currency, place)
            _uiState.update { it.copy(isSaving = false, saved = true) }
        }
    }

    companion object {
        /** The pause after the last keystroke before the text is looked up. */
        const val LOOKUP_DELAY_MILLIS = 300L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                val container = appContainer()
                TripFormViewModel(
                    tripId = handle.toRoute<TripForm>().tripId,
                    repository = container.tripRepository,
                    authRepository = container.authRepository,
                    currencyRepository = container.currencyRepository,
                    destinations = container.destinationSuggestions,
                )
            }
        }
    }
}
