package com.example.planyourtrip.ui.tripdetails.split

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
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseRepository
import com.example.planyourtrip.data.trip.InMemoryMergeSuggestionStore
import com.example.planyourtrip.data.trip.MergeSuggestionStore
import com.example.planyourtrip.data.trip.RemoveTravelerResult
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.SettlementRepository
import com.example.planyourtrip.data.trip.TextLimits
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerRepository
import com.example.planyourtrip.data.trip.TripMemberRepository
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.data.trip.limitTo
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.common.SavedFormState
import com.example.planyourtrip.ui.common.WhileUiSubscribed
import com.example.planyourtrip.ui.common.split.MergeSuggestion
import com.example.planyourtrip.ui.common.split.Transfer
import com.example.planyourtrip.ui.common.split.balances
import com.example.planyourtrip.ui.common.split.isTravelerUsed
import com.example.planyourtrip.ui.common.split.mergeSuggestions
import com.example.planyourtrip.ui.common.split.planTravelerMerge
import com.example.planyourtrip.ui.common.split.sharedExpenseCount
import com.example.planyourtrip.ui.common.split.simplifyTransfers
import com.example.planyourtrip.ui.common.split.travelerOrder
import com.example.planyourtrip.ui.common.split.validateTravelerName
import com.example.planyourtrip.ui.navigation.TripDetails
import java.io.Serializable
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Travelers, "Who owes whom" and "Settle up" of one trip (Budget tab). Created with the TripDetails back stack entry
 * as owner, so it reads [TripDetails] from the SavedStateHandle. The budget numbers (my share) live in
 * `TripDetailsViewModel`; this ViewModel never changes them: settlements only move balances.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplitViewModel(
    private val tripId: Long,
    tripRepository: TripRepository,
    expenseRepository: ExpenseRepository,
    private val travelerRepository: TravelerRepository,
    private val settlementRepository: SettlementRepository,
    private val authRepository: AuthRepository,
    private val clock: Clock,
    /** Where balances and transfers are computed; tests pass their test dispatcher. */
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Names of the trip's other members (shared trips). */
    memberRepository: TripMemberRepository = TripMemberRepository.None,
    /** The "Merge with the traveler?" suggestions answered "Keep separate" (this device only). */
    private val suggestionStore: MergeSuggestionStore = InMemoryMergeSuggestionStore(),
    /** Keeps an open traveler dialog or settle-up sheet across process death. */
    savedState: SavedStateHandle? = null,
) : ViewModel() {

    /** The latest expenses and settlements, for the "can't be removed" check when a traveler is tapped. */
    private var expenses: List<Expense> = emptyList()
    private var settlements: List<Settlement> = emptyList()
    private var tripUuid: String = ""

    /** A merge is being written (set synchronously, so a double tap merges once). */
    private val merging = MutableStateFlow(false)

    private val loadedState = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) return@flatMapLatest flowOf(SplitUiState.NotFound)
            combine(
                tripRepository.observeTrip(tripId, user.uid),
                expenseRepository.observeExpenses(tripId, user.uid),
                travelerRepository.observeTravelers(tripId, user.uid),
                settlementRepository.observeSettlements(tripId, user.uid),
                memberRepository.observeMembers(tripId, user.uid),
            ) { trip, expenseList, travelers, settlementList, members ->
                if (trip == null) return@combine SplitUiState.NotFound
                expenses = expenseList
                settlements = settlementList
                tripUuid = trip.uuid
                val memberIds = trip.memberIds.ifEmpty { listOf(user.uid) }
                val order = travelerOrder(user.uid, trip.memberIds, travelers)
                SplitUiState.Loaded(
                    me = user.uid,
                    travelers = travelers,
                    transfers = simplifyTransfers(balances(expenseList, settlementList, order), order, user.uid),
                    sharedExpenseCount = sharedExpenseCount(expenseList),
                    settlements = settlementList,
                    memberIds = memberIds,
                    members = members,
                    suggestions = mergeSuggestions(user.uid, memberIds, members, travelers, expenseList, settlementList),
                )
            }
        }
        .flowOn(computeDispatcher)

    val uiState: StateFlow<SplitUiState> =
        combine(loadedState, suggestionStore.dismissed, merging) { state, dismissed, isMerging ->
            if (state !is SplitUiState.Loaded) return@combine state
            state.copy(
                suggestion = state.suggestions.firstOrNull { suggestionKey(state.me, it) !in dismissed },
                isMerging = isMerging,
            )
        }.stateIn(viewModelScope, WhileUiSubscribed, SplitUiState.Loading)

    private fun suggestionKey(me: String, suggestion: MergeSuggestion) =
        MergeSuggestionStore.key(me, tripUuid, suggestion.traveler.uuid, suggestion.memberKey)

    // ---- Merging a name-only traveler into a member who joined ----

    /**
     * "Merge": the traveler's expenses, splits and settlements move to the member and the traveler is removed, in one
     * change (only when every balance stays the same; the repository checks again against its current rows).
     */
    fun onMergeSuggestion() {
        val suggestion = loaded()?.suggestion ?: return
        if (suggestion.blocked != null || merging.value) return
        val owner = ownerId ?: return
        merging.value = true
        viewModelScope.launch {
            try {
                travelerRepository.mergeIntoMember(tripId, owner, suggestion.traveler.id, suggestion.memberKey, ::planTravelerMerge)
            } finally {
                merging.value = false
            }
        }
    }

    /** "Keep separate" (or "OK" when it can't be merged): never suggested again on this device. */
    fun onDismissSuggestion() {
        val state = loaded() ?: return
        val suggestion = state.suggestion ?: return
        if (merging.value) return
        suggestionStore.dismiss(suggestionKey(state.me, suggestion))
    }

    private val ownerId: String? get() = authRepository.currentUser.value?.uid

    private fun loaded(): SplitUiState.Loaded? = uiState.value as? SplitUiState.Loaded

    // ---- Travelers ----

    private val _travelerDialog = SavedFormState<TravelerDialogState?, TravelerDialogDraft>(
        null, savedState, KEY_TRAVELER_DIALOG,
    ) { dialog -> dialog?.let { TravelerDialogDraft(it.editing?.uuid, it.name) } }

    /** The open add / edit traveler dialog, or null. */
    val travelerDialog: StateFlow<TravelerDialogState?> = _travelerDialog.flow

    /** "+ Add": an empty name dialog. */
    fun onAddTraveler() {
        if (loaded() == null) return
        _travelerDialog.update { TravelerDialogState() }
    }

    /** A traveler's avatar: rename or remove ("You" is never passed here). */
    fun onTravelerClick(traveler: Traveler) {
        _travelerDialog.update {
            TravelerDialogState(
                editing = traveler,
                name = traveler.name,
                removeBlocked = isTravelerUsed(traveler.uuid, expenses, settlements),
            )
        }
    }

    fun onTravelerNameChange(name: String) =
        _travelerDialog.update { it?.copy(name = name.limitTo(TextLimits.TRAVELER_NAME), error = null) }

    fun onDismissTravelerDialog() {
        if (_travelerDialog.value?.isSaving == true) return
        _travelerDialog.update { null }
    }

    /** Adds or renames: the name must not be blank, "You" or another traveler's name. */
    fun onSaveTraveler() {
        val dialog = _travelerDialog.value ?: return
        if (dialog.isSaving) return
        val owner = ownerId ?: return
        val travelers = loaded()?.travelers ?: return
        val error = validateTravelerName(dialog.name, travelers, editingKey = dialog.editing?.uuid)
        if (error != null) {
            _travelerDialog.update { dialog.copy(error = error) }
            return
        }
        _travelerDialog.update { dialog.copy(isSaving = true) }
        viewModelScope.launch {
            val editing = dialog.editing
            if (editing == null) {
                travelerRepository.addTraveler(tripId, owner, dialog.name)
            } else {
                travelerRepository.renameTraveler(editing.id, owner, dialog.name)
            }
            _travelerDialog.update { null }
        }
    }

    /** Removes the edited traveler, unless an expense or settlement uses them (the dialog then says why). */
    fun onRemoveTraveler() {
        val dialog = _travelerDialog.value ?: return
        val traveler = dialog.editing ?: return
        if (dialog.isSaving || dialog.removeBlocked) return
        val owner = ownerId ?: return
        _travelerDialog.update { dialog.copy(isSaving = true) }
        viewModelScope.launch {
            when (travelerRepository.removeTraveler(traveler.id, owner)) {
                // Used on another screen in the meantime: keep the dialog open and explain.
                RemoveTravelerResult.IN_USE ->
                    _travelerDialog.update { dialog.copy(isSaving = false, removeBlocked = true) }
                RemoveTravelerResult.REMOVED, RemoveTravelerResult.NOT_FOUND -> _travelerDialog.update { null }
            }
        }
    }

    // ---- Settle up ----

    private val _settleSheet = SavedFormState<SettleSheetState?, SettleSheetDraft>(
        null, savedState, KEY_SETTLE_SHEET,
    ) { sheet -> sheet?.let { SettleSheetDraft(it.page, it.selected?.fromKey, it.selected?.toKey, it.amount) } }

    /** The open "Settle up" sheet, or null. */
    val settleSheet: StateFlow<SettleSheetState?> = _settleSheet.flow

    init {
        restoreDialogs()
    }

    /**
     * After process death: the traveler dialog or settle-up sheet that was open comes back once the trip is loaded,
     * as long as its traveler (or transfer) still exists.
     */
    private fun restoreDialogs() {
        val travelerDraft = _travelerDialog.restored
        val settleDraft = _settleSheet.restored
        if (travelerDraft == null && settleDraft == null) return
        viewModelScope.launch {
            val state = uiState.filterIsInstance<SplitUiState.Loaded>().first()
            if (travelerDraft != null && _travelerDialog.value == null) {
                val editing = travelerDraft.editingUuid?.let { uuid -> state.travelers.firstOrNull { it.uuid == uuid } }
                if (travelerDraft.editingUuid == null || editing != null) {
                    _travelerDialog.update {
                        TravelerDialogState(
                            editing = editing,
                            name = travelerDraft.name,
                            removeBlocked = editing != null && isTravelerUsed(editing.uuid, expenses, settlements),
                        )
                    }
                }
            }
            if (settleDraft != null && _settleSheet.value == null) {
                val transfer = state.transfers.firstOrNull {
                    it.fromKey == settleDraft.fromKey && it.toKey == settleDraft.toKey
                }
                _settleSheet.update {
                    SettleSheetState(
                        page = settleDraft.page,
                        selected = transfer,
                        amount = if (transfer != null) settleDraft.amount else "",
                    )
                }
            }
        }
    }

    /** "Settle up": the open transfers; with only one, it is picked already. */
    fun onSettleUp() {
        val transfers = loaded()?.transfers ?: return
        _settleSheet.update { transfers.singleOrNull()?.let(::selection) ?: SettleSheetState() }
    }

    /** "History" link (in the sheet, or in the card when everything is settled). */
    fun onShowHistory() {
        if (loaded() == null) return
        _settleSheet.update { (it ?: SettleSheetState()).copy(page = SettlePage.HISTORY) }
    }

    fun onShowTransfers() = _settleSheet.update { it?.copy(page = SettlePage.TRANSFERS) }

    fun onSelectTransfer(transfer: Transfer) = _settleSheet.update { sheet ->
        if (sheet == null || sheet.isSaving) sheet else selection(transfer)
    }

    fun onSettleAmountChange(amount: String) = _settleSheet.update { it?.copy(amount = amount, error = null) }

    fun onDismissSettle() {
        if (_settleSheet.value?.isSaving == true) return
        _settleSheet.update { null }
    }

    /** "Mark as paid": saves who paid whom, how much (≤ the open amount; less = partial) and today's date. */
    fun onMarkAsPaid() {
        val sheet = _settleSheet.value ?: return
        val transfer = sheet.selected ?: return
        if (sheet.isSaving) return
        val owner = ownerId ?: return
        val error = validateSettleAmount(sheet.amount, transfer.amountAgorot)
        if (error != null) {
            _settleSheet.update { sheet.copy(error = error) }
            return
        }
        val agorot = checkNotNull(Money.parseAgorot(sheet.amount))
        _settleSheet.update { sheet.copy(isSaving = true) }
        viewModelScope.launch {
            settlementRepository.addSettlement(
                tripId, owner, transfer.fromKey, transfer.toKey, agorot, LocalDate.now(clock),
            )
            _settleSheet.update { null }
        }
    }

    private fun selection(transfer: Transfer) =
        SettleSheetState(selected = transfer, amount = Money.toInput(transfer.amountAgorot))

    // ---- History ----

    private val _settlementToDelete = MutableStateFlow<Settlement?>(null)

    /** The settlement whose delete is being confirmed, or null. */
    val settlementToDelete: StateFlow<Settlement?> = _settlementToDelete.asStateFlow()

    fun onDeleteSettlement(settlement: Settlement) {
        _settlementToDelete.value = settlement
    }

    fun onDismissDeleteSettlement() {
        _settlementToDelete.value = null
    }

    /** Deletes the settlement: the debt it paid is open again. */
    fun onConfirmDeleteSettlement() {
        val settlement = _settlementToDelete.value ?: return
        val owner = ownerId ?: return
        _settlementToDelete.value = null
        viewModelScope.launch { settlementRepository.deleteSettlement(settlement.id, owner) }
    }

    companion object {
        private const val KEY_TRAVELER_DIALOG = "splitTravelerDialog"
        private const val KEY_SETTLE_SHEET = "splitSettleSheet"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val handle: SavedStateHandle = createSavedStateHandle()
                val container = appContainer()
                SplitViewModel(
                    tripId = handle.toRoute<TripDetails>().tripId,
                    tripRepository = container.tripRepository,
                    expenseRepository = container.expenseRepository,
                    travelerRepository = container.travelerRepository,
                    settlementRepository = container.settlementRepository,
                    authRepository = container.authRepository,
                    clock = container.clock,
                    memberRepository = container.tripMemberRepository,
                    suggestionStore = container.mergeSuggestionStore,
                    savedState = handle,
                )
            }
        }
    }
}

/** An open traveler dialog, kept across process death: the edited traveler (null = adding) and the typed name. */
private data class TravelerDialogDraft(val editingUuid: String?, val name: String) : Serializable

/** An open settle-up sheet, kept across process death: its page, the picked transfer and the typed amount. */
private data class SettleSheetDraft(
    val page: SettlePage,
    val fromKey: String?,
    val toKey: String?,
    val amount: String,
) : Serializable
