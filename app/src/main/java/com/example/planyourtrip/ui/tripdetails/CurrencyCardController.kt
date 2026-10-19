package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.currency.CurrencyRepository
import com.example.planyourtrip.data.currency.ILS
import com.example.planyourtrip.data.currency.isStale
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.common.CurrencyPickerState
import com.example.planyourtrip.ui.common.WhileUiSubscribed
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The Overview currency card (rate, refresh, "Try again") and the currency picker that changes the trip's currency. */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrencyCardController(
    private val tripId: Long,
    tripData: Flow<TripData?>,
    private val tripRepository: TripRepository,
    private val authRepository: AuthRepository,
    private val currencyRepository: CurrencyRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    /** Currency code of the loaded trip; null when it has none or isn't loaded. */
    private val currentCurrency: () -> String?,
) {
    /** Currency code of the loaded trip; null inside [Selected] = no currency chosen yet. */
    private data class Selected(val code: String?)

    /** Code whose last refresh failed, so the card can tell "loading" from "unavailable". */
    private val refreshFailedFor = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null

    /** The currency the Overview last showed, to notice a change (made on the card or in the trip form). */
    private var shownCode: String? = null
    private var hasShown = false

    /**
     * The Overview currency card; null until the trip is loaded (nothing is drawn). The converter's text is
     * not here: it lives in the card, so typing never waits for this flow.
     */
    val card: StateFlow<CurrencyCardState?> = tripData
        .map { data -> data?.let { Selected(it.trip.currencyCode) } }
        .distinctUntilChanged()
        .flatMapLatest { selected ->
            val code = selected?.code
            when {
                selected == null -> flowOf(null)
                code == null -> flowOf(CurrencyCardState.NoCurrency)
                code == ILS -> flowOf(CurrencyCardState.Shekel)
                else -> combine(currencyRepository.observeRate(code), refreshFailedFor) { rate, failedFor ->
                    when {
                        rate != null -> CurrencyCardState.Rate(code, rate.ilsPerUnit, rate.date)
                        failedFor == code -> CurrencyCardState.Unavailable(code)
                        else -> CurrencyCardState.Loading(code)
                    }
                }
            }
        }
        .stateIn(scope, WhileUiSubscribed, null)

    /**
     * Called whenever the Overview is shown or the trip's currency changes. A currency different from the one
     * shown before is fetched right away; otherwise the rate is refreshed only when 12 hours old or more.
     */
    fun onOverviewShown() {
        val code = currentCurrency()
        val changed = hasShown && code != shownCode
        hasShown = true
        shownCode = code
        if (code != null) refreshRate(code, force = changed)
    }

    /** "Try again" on the card. */
    fun onRetryRate() {
        currentCurrency()?.let { refreshRate(it, force = true) }
    }

    private fun refreshRate(code: String, force: Boolean) {
        if (code == ILS) return
        if (refreshJob?.isActive == true) {
            if (!force) return
            refreshJob?.cancel()
        }
        refreshJob = scope.launch {
            if (!force) {
                val cached = currencyRepository.observeRate(code).first()
                if (!isStale(cached?.fetchedAt, clock.instant())) return@launch
            }
            refreshFailedFor.value = null
            val ok = currencyRepository.refreshRate(code)
            refreshFailedFor.value = if (ok) null else code
        }
    }

    // ---------- Currency picker ----------

    private val _picker = MutableStateFlow<CurrencyPickerState?>(null)

    /** The open currency picker, or null when closed. */
    val picker: StateFlow<CurrencyPickerState?> = _picker.asStateFlow()

    /** "Choose currency" or a tap on the currency chip: opens the picker and loads the list. */
    fun onChooseCurrency() {
        _picker.value = CurrencyPickerState(selectedCode = currentCurrency())
        loadCurrencies()
    }

    fun onRetryCurrencies() = loadCurrencies()

    private fun loadCurrencies() {
        _picker.update { it?.loading() }
        scope.launch {
            val list = currencyRepository.currencies()
            _picker.update { it?.loaded(list) }
        }
    }

    fun onQueryChange(query: String) = _picker.update { it?.withQuery(query) }

    fun onDismissPicker() {
        _picker.value = null
    }

    /**
     * Saves [code] on the trip, closes the picker and fetches the new rate right away. The card clears its
     * converter fields itself because they are keyed by the currency code.
     */
    fun onCurrencySelected(code: String) {
        val ownerId = authRepository.currentUser.value?.uid ?: return
        _picker.value = null
        if (code == currentCurrency()) return
        refreshFailedFor.value = null
        hasShown = true
        shownCode = code
        scope.launch { tripRepository.setCurrency(tripId, ownerId, code) }
        refreshRate(code, force = true)
    }
}
