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
import com.example.planyourtrip.data.reminder.ReminderPrompt
import com.example.planyourtrip.data.sync.NetworkMonitor
import com.example.planyourtrip.data.trip.TextLimits
import com.example.planyourtrip.data.trip.TripPlace
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.data.trip.limitTo
import com.example.planyourtrip.data.trip.place
import com.example.planyourtrip.ui.common.CurrencyPickerState
import com.example.planyourtrip.ui.common.SavedFormState
import com.example.planyourtrip.ui.common.currencyName
import com.example.planyourtrip.ui.navigation.TripForm
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.Serializable
import java.time.LocalDate

/** New trip when [tripId] is null; otherwise edits destination, dates, currency and place of that trip. */
class TripFormViewModel(
    private val tripId: Long?,
    private val repository: TripRepository,
    private val authRepository: AuthRepository,
    /** For the list of currencies in the picker (and which currency a picked place may preselect). */
    private val currencyRepository: CurrencyRepository,
    /** Places for the destination field (the built-in table at once, then Photon). */
    private val destinations: DestinationSuggestions,
    /** Told about a new trip: may ask for the notification permission (trip reminders). */
    private val reminderPrompt: ReminderPrompt = ReminderPrompt.None,
    /** Whether a failed lookup was for lack of internet (then the form says so); null = unknown, never said. */
    private val network: NetworkMonitor? = null,
    /** Keeps the form's draft across process death. */
    savedState: SavedStateHandle? = null,
) : ViewModel() {

    /** The running destination lookup; a new keystroke cancels it (and its request). */
    private var lookup: Job? = null
    private var preselection: Job? = null

    /** The service's currencies, once loaded (used for names). */
    private var currencies: List<Currency> = emptyList()

    private val form = SavedFormState(
        TripFormUiState(isEditing = tripId != null, isLoading = tripId != null), savedState, KEY_DRAFT,
    ) { if (it.isLoading) null else TripFormDraft.of(it) }
    val uiState: StateFlow<TripFormUiState> = form.flow

    init {
        if (tripId != null) loadTrip(tripId) else form.restored?.let { draft -> form.update { draft.applyTo(it) } }
    }

    /** Fills the fields once with the trip's current values (only the owner's trips). */
    private fun loadTrip(id: Long) {
        val ownerId = authRepository.currentUser.value?.uid ?: return
        viewModelScope.launch {
            val trip = repository.observeTrip(id, ownerId).first()
            val restored = form.restored
            form.update {
                when {
                    // After process death the form comes back as the user left it.
                    restored != null -> restored.applyTo(it.copy(isLoading = false))
                    trip == null -> it.copy(isLoading = false)
                    else -> it.copy(
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
    }

    /**
     * Typing: any change of the text forgets the picked place (until a suggestion is picked again). From
     * [DestinationSuggestions.MIN_QUERY_LENGTH] characters the built-in table's places show at once; after a short
     * pause Photon is asked ([TripFormUiState.isSearching] meanwhile) and its places are added under them.
     */
    fun onDestinationChange(text: String) {
        val value = text.limitTo(TextLimits.DESTINATION)
        val changed = value != form.value.destination
        form.update {
            it.copy(
                destination = value,
                destinationError = null,
                place = if (changed) null else it.place,
                searchNeedsInternet = it.searchNeedsInternet && !changed,
            )
        }
        if (!changed) return
        lookup?.cancel()
        if (value.trim().length < DestinationSuggestions.MIN_QUERY_LENGTH) {
            form.update { it.copy(suggestions = emptyList(), isSearching = false) }
            return
        }
        // A search already showing keeps its bar through the pause (no flicker); it ends with the new answer.
        val instant = destinations.instant(value)
        form.update { it.copy(suggestions = instant) }
        lookup = viewModelScope.launch {
            delay(LOOKUP_DELAY_MILLIS)
            form.update { if (it.destination == value) it.copy(isSearching = true) else it }
            val remote = destinations.remote(value)
            // A slow answer for older text is never shown; a failed one leaves the instant rows. Only when there are
            // none and the device is offline does the form say that more places need the internet.
            val offline = network?.isOnline?.value == false
            form.update {
                if (it.destination != value) it
                else it.copy(
                    suggestions = DestinationSuggestions.merge(instant, remote.orEmpty()),
                    isSearching = false,
                    searchNeedsInternet = remote == null && instant.isEmpty() && offline,
                )
            }
        }
    }

    /** Picking fills "London, United Kingdom", remembers the country and coordinates, and may preselect a currency. */
    fun onSuggestionPicked(suggestion: PlaceSuggestion) {
        lookup?.cancel()
        form.update {
            it.copy(
                destination = suggestion.label,
                destinationError = null,
                suggestions = emptyList(),
                isSearching = false,
                searchNeedsInternet = false,
                place = suggestion.toTripPlace(),
            )
        }
        preselectCurrency(suggestion.countryCode)
    }

    /** The field lost focus: the list and the bar close (the text and a picked place stay). */
    fun onSuggestionsDismissed() {
        lookup?.cancel()
        form.update { it.copy(suggestions = emptyList(), isSearching = false, searchNeedsInternet = false) }
    }

    /**
     * A new trip without a currency chosen by the user gets the picked country's currency, if the app offers it;
     * a currency preselected for an earlier pick follows the new pick. Editing a trip never changes its currency.
     */
    private fun preselectCurrency(countryCode: String?) {
        val state = form.value
        if (state.isEditing || (state.currencyCode != null && !state.currencyPreselected)) return
        preselection?.cancel()
        preselection = viewModelScope.launch {
            val code = countryCode?.let(::countryCurrency)
            val list = if (code != null) currencyRepository.currencies() else null
            if (list != null) currencies = list
            val offered = code != null && list.orEmpty().any { it.code == code }
            form.update {
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
        form.update { it.copy(startDate = date, startError = null, endError = null) }

    fun onEndDateChange(date: LocalDate) =
        form.update { it.copy(endDate = date, startError = null, endError = null) }

    /** Opens the searchable currency list (nothing preselected for a new trip). */
    fun onChooseCurrency() {
        form.update { it.copy(currencyPicker = CurrencyPickerState(selectedCode = it.currencyCode)) }
        loadCurrencies()
    }

    fun onRetryCurrencies() = loadCurrencies()

    private fun loadCurrencies() {
        form.update { it.copy(currencyPicker = it.currencyPicker?.loading()) }
        viewModelScope.launch {
            val list = currencyRepository.currencies()
            if (list != null) currencies = list
            form.update { it.copy(currencyPicker = it.currencyPicker?.loaded(list)) }
        }
    }

    fun onCurrencyQueryChange(query: String) =
        form.update { it.copy(currencyPicker = it.currencyPicker?.withQuery(query)) }

    fun onDismissCurrencyPicker() = form.update { it.copy(currencyPicker = null) }

    fun onCurrencySelected(code: String) {
        preselection?.cancel()
        form.update {
            it.copy(
                currencyCode = code, currencyName = currencyName(code, currencies), currencyPicker = null,
                currencyPreselected = false,
            )
        }
    }

    /** The currency is optional: the clear icon removes it. */
    fun onClearCurrency() {
        preselection?.cancel()
        form.update { it.copy(currencyCode = null, currencyName = null, currencyPreselected = false) }
    }

    fun onSave() {
        val state = form.value
        if (state.isLoading || state.isSaving || state.saved) return

        val validated = state.copy(
            destinationError = TripValidator.validateDestination(state.destination),
            startError = TripValidator.validateStart(state.startDate),
            endError = TripValidator.validateEnd(state.startDate, state.endDate),
        )
        form.update { validated }
        if (validated.destinationError != null || validated.startError != null || validated.endError != null) return

        // Behind Login there is always a user; without one there is nobody to own the trip.
        val ownerId = authRepository.currentUser.value?.uid ?: return
        val destination = validated.destination.trim()
        val start = checkNotNull(validated.startDate)
        val end = checkNotNull(validated.endDate)
        // Free text (nothing picked, or edited since) is saved without a country or coordinates.
        val place = validated.place ?: TripPlace.NONE
        lookup?.cancel()
        form.update {
            it.copy(isSaving = true, suggestions = emptyList(), isSearching = false, searchNeedsInternet = false)
        }
        viewModelScope.launch {
            val currency = validated.currencyCode
            if (tripId == null) {
                repository.addTrip(ownerId, destination, start, end, currency, place)
                reminderPrompt.onTripAdded(start)
            } else {
                repository.updateTrip(tripId, ownerId, destination, start, end, currency, place)
            }
            form.update { it.copy(isSaving = false, saved = true) }
        }
    }

    companion object {
        /** The pause after the last keystroke before the text is looked up. */
        const val LOOKUP_DELAY_MILLIS = 300L

        private const val KEY_DRAFT = "tripFormDraft"

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
                    reminderPrompt = container.reminderPermissionPrompt,
                    network = container.networkMonitor,
                    savedState = handle,
                )
            }
        }
    }
}

/** What the user entered and chose in the trip form, kept across process death (see [SavedFormState]). */
private data class TripFormDraft(
    val destination: String,
    val countryCode: String?,
    val latitude: Double?,
    val longitude: Double?,
    val hasPlace: Boolean,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val currencyCode: String?,
    val currencyName: String?,
    val currencyPreselected: Boolean,
) : Serializable {
    fun applyTo(state: TripFormUiState) = state.copy(
        destination = destination,
        place = if (hasPlace) TripPlace(countryCode, latitude, longitude) else null,
        startDate = startDate,
        endDate = endDate,
        currencyCode = currencyCode,
        currencyName = currencyName,
        currencyPreselected = currencyPreselected,
    )

    companion object {
        fun of(state: TripFormUiState) = TripFormDraft(
            destination = state.destination,
            countryCode = state.place?.countryCode,
            latitude = state.place?.latitude,
            longitude = state.place?.longitude,
            hasPlace = state.place != null,
            startDate = state.startDate,
            endDate = state.endDate,
            currencyCode = state.currencyCode,
            currencyName = state.currencyName,
            currencyPreselected = state.currencyPreselected,
        )
    }
}
