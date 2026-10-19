package com.example.planyourtrip.ui.tripdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.ui.common.components.RefreshBox
import com.example.planyourtrip.ui.common.delete.DeleteTripViewModel
import com.example.planyourtrip.ui.common.refresh.RefreshViewModel
import com.example.planyourtrip.ui.common.share.ShareTripViewModel
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.tripdetails.components.BudgetDialog
import com.example.planyourtrip.ui.tripdetails.components.CurrencyCardActions
import com.example.planyourtrip.ui.tripdetails.components.ItineraryActions
import com.example.planyourtrip.ui.tripdetails.components.TripHeader
import com.example.planyourtrip.ui.tripdetails.members.InviteViewModel
import com.example.planyourtrip.ui.tripdetails.members.MemberActions
import com.example.planyourtrip.ui.tripdetails.prep.PrepActions
import com.example.planyourtrip.ui.tripdetails.prep.PrepUiState
import com.example.planyourtrip.ui.tripdetails.prep.PrepViewModel
import com.example.planyourtrip.ui.tripdetails.prep.prepTab
import com.example.planyourtrip.ui.tripdetails.prep.rememberPrepActions
import com.example.planyourtrip.ui.tripdetails.split.SplitActions
import com.example.planyourtrip.ui.tripdetails.split.SplitUiState
import com.example.planyourtrip.ui.tripdetails.split.SplitViewModel
import java.time.LocalDate

@Composable
fun TripDetailsScreen(
    onBack: () -> Unit,
    onEditTrip: () -> Unit,
    onAddExpense: () -> Unit,
    onExpenseClick: (Long) -> Unit,
    onAddActivity: (LocalDate) -> Unit,
    onActivityClick: (Long) -> Unit,
    onOpenAssistant: () -> Unit,
    /** "Delete trip" was confirmed: go back to Home, which deletes the trip there. */
    onTripDeleted: (Long) -> Unit,
    /** "Leave trip" was confirmed (a shared trip someone else owns): go back to Home, which leaves it there. */
    onTripLeft: (Long) -> Unit = {},
    /** The tab to open on (a tapped trip reminder opens Prep). */
    initialTab: TripTab = TripTab.OVERVIEW,
    viewModel: TripDetailsViewModel = viewModel(factory = TripDetailsViewModel.Factory),
    shareViewModel: ShareTripViewModel = viewModel(factory = ShareTripViewModel.Factory),
    deleteViewModel: DeleteTripViewModel = viewModel(factory = DeleteTripViewModel.Factory),
    prepViewModel: PrepViewModel = viewModel(factory = PrepViewModel.Factory),
    splitViewModel: SplitViewModel = viewModel(factory = SplitViewModel.Factory),
    refreshViewModel: RefreshViewModel = viewModel(factory = RefreshViewModel.Factory),
    inviteViewModel: InviteViewModel = viewModel(factory = InviteViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refresh by refreshViewModel.state.collectAsStateWithLifecycle()
    val budgetDialog by viewModel.budgetDialog.collectAsStateWithLifecycle()
    val currencyCard by viewModel.currencyCard.collectAsStateWithLifecycle()
    val headerPhoto by viewModel.headerPhoto.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    TripDetailsSnackbars(viewModel, refreshViewModel, snackbarHostState)
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    // Opening the Overview refreshes a rate older than 12 hours; a changed currency is fetched right away.
    val loaded = state is TripDetailsUiState.Loaded
    val currencyCode = (state as? TripDetailsUiState.Loaded)?.trip?.currencyCode
    LaunchedEffect(selectedTab, loaded, currencyCode) {
        if (selectedTab == TripTab.OVERVIEW && loaded) viewModel.onOverviewShown()
    }

    val openUrl = rememberOpenMapsUrl(snackbarHostState)
    val destination = (state as? TripDetailsUiState.Loaded)?.trip?.destination.orEmpty()

    val prepState by prepViewModel.uiState.collectAsStateWithLifecycle()
    val prepActions = rememberPrepActions(prepViewModel, snackbarHostState)
    val splitState by splitViewModel.uiState.collectAsStateWithLifecycle()
    TripDetailsContent(
        state = state,
        selectedTab = selectedTab,
        budgetDialog = budgetDialog,
        currencyCard = currencyCard,
        currencyActions = rememberCurrencyActions(viewModel),
        itineraryActions = rememberItineraryActions(
            viewModel, onAddActivity, onActivityClick, openUrl, destination,
            onSeeFullPlan = { selectedTab = TripTab.PLAN },
        ),
        budgetActions = rememberBudgetActions(viewModel, onAddExpense, onExpenseClick),
        onTabSelected = { selectedTab = it },
        onBack = onBack,
        onEditTrip = onEditTrip,
        onShareTrip = shareViewModel::onShareTrip,
        onDeleteTrip = deleteViewModel::onRemoveTrip,
        onOpenAssistant = onOpenAssistant,
        memberActions = remember(inviteViewModel) {
            MemberActions(onShowMembers = inviteViewModel::onShowMembers, onInvite = inviteViewModel::onInvite)
        },
        snackbarHostState = snackbarHostState,
        onOpenMaps = { destination -> openUrl(googleMapsSearchUrl(destination)) },
        prep = prepState,
        prepActions = prepActions,
        split = splitState,
        splitActions = rememberSplitActions(splitViewModel),
        isRefreshing = refresh.isRefreshing,
        onRefresh = refreshViewModel::onRefresh,
        headerPhotoUrl = headerPhoto?.cardUrl,
    )

    TripDetailsOverlays(
        state = state,
        viewModel = viewModel,
        shareViewModel = shareViewModel,
        deleteViewModel = deleteViewModel,
        inviteViewModel = inviteViewModel,
        prepViewModel = prepViewModel,
        splitViewModel = splitViewModel,
        snackbarHostState = snackbarHostState,
        onTripDeleted = onTripDeleted,
        onTripLeft = onTripLeft,
    )
}

@Composable
fun TripDetailsContent(
    state: TripDetailsUiState,
    selectedTab: TripTab,
    budgetDialog: BudgetDialogState?,
    currencyCard: CurrencyCardState?,
    currencyActions: CurrencyCardActions,
    itineraryActions: ItineraryActions,
    budgetActions: BudgetActions,
    onTabSelected: (TripTab) -> Unit,
    onBack: () -> Unit,
    onEditTrip: () -> Unit,
    onOpenAssistant: () -> Unit,
    onShareTrip: (Long) -> Unit = {},
    /** "Delete trip" (or, for a shared trip someone else owns, "Leave trip") in the header menu. */
    onDeleteTrip: (Trip) -> Unit = {},
    /** The Overview's "Traveling together" card. */
    memberActions: MemberActions = MemberActions.None,
    /** Messages over every tab ("Activity deleted" + "Undo"). */
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    /** The map card was tapped: open Google Maps on this destination. */
    onOpenMaps: (String) -> Unit = {},
    /** The Prep tab's checklists and documents (their own ViewModel). */
    prep: PrepUiState = PrepUiState.Loading,
    prepActions: PrepActions = PrepActions.None,
    /** The Budget tab's travelers, "Who owes whom" and "Settle up" (their own ViewModel). */
    split: SplitUiState = SplitUiState.Loading,
    splitActions: SplitActions = SplitActions.None,
    /** Pull to refresh (every tab): a full sync with the cloud. */
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    /** The header's destination photo (about 960 px); null = the gradient only. */
    headerPhotoUrl: String? = null,
) {
    when (state) {
        // Nothing is drawn until Room answers, so no placeholder flashes.
        TripDetailsUiState.Loading ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        TripDetailsUiState.NotFound -> NotFound(onBack)
        is TripDetailsUiState.Loaded -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // The header draws behind the status bar; the navigation bar pads itself.
            contentWindowInsets = WindowInsets(0),
            bottomBar = { TripNavigationBar(selectedTab, onTabSelected) },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // On every tab, above the navigation bar; the Budget tab adds "Add expense" at the bottom left.
            floatingActionButton = { TripFabs(selectedTab, budgetActions.onAddExpense, onOpenAssistant) },
            // Centered = the full-width row above lines the two buttons up with both edges.
            floatingActionButtonPosition = if (selectedTab == TripTab.BUDGET) FabPosition.Center else FabPosition.End,
        ) { innerPadding ->
            // The header draws behind the status bar: the spinner appears below it.
            RefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                indicatorTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            ) {
                // A fresh list per tab, so a newly selected tab opens at the top.
                key(selectedTab) {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        // The buttons float over the list; this end padding lets the last card scroll fully above them.
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + FabClearance),
                        verticalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        tripHeaderItem(state, headerPhotoUrl, onBack, onEditTrip, onShareTrip, onDeleteTrip)
                        when (selectedTab) {
                            TripTab.OVERVIEW ->
                                overviewTab(
                                    state, currencyCard, currencyActions, itineraryActions,
                                    onTabSelected, budgetActions.onEditBudget, onOpenMaps, memberActions,
                                )
                            TripTab.BUDGET -> budgetTab(state, split, splitActions, budgetActions)
                            TripTab.PREP -> prepTab(prep, prepActions)
                            TripTab.PLAN -> planTab(state, itineraryActions)
                        }
                    }
                    StatusBarShade(listState)
                }
            }
        }
    }

    if (budgetDialog != null) {
        BudgetDialog(
            state = budgetDialog,
            onInputChange = budgetActions.onBudgetInputChange,
            onSave = budgetActions.onSaveBudget,
            onDismiss = budgetActions.onDismissBudget,
        )
    }
}

/** The floating buttons: the assistant on every tab, with "Add expense" at the bottom left on the Budget tab. */
@Composable
private fun TripFabs(selectedTab: TripTab, onAddExpense: () -> Unit, onOpenAssistant: () -> Unit) {
    if (selectedTab == TripTab.BUDGET) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l),
        ) {
            AddExpenseFab(onAddExpense)
            Spacer(Modifier.weight(1f).widthIn(min = Spacing.l))
            AssistantFab(onOpenAssistant)
        }
    } else {
        AssistantFab(onOpenAssistant)
    }
}

/** The hero header, the first item of every tab. */
private fun LazyListScope.tripHeaderItem(
    state: TripDetailsUiState.Loaded,
    headerPhotoUrl: String?,
    onBack: () -> Unit,
    onEditTrip: () -> Unit,
    onShareTrip: (Long) -> Unit,
    onDeleteTrip: (Trip) -> Unit,
) {
    item(key = HEADER_KEY) {
        TripHeader(
            state.trip,
            state.status,
            onBack = onBack,
            onEdit = onEditTrip,
            onShare = { onShareTrip(state.trip.id) },
            onDelete = { onDeleteTrip(state.trip) },
            isOwner = state.members?.isOwner ?: true,
            photoUrl = headerPhotoUrl,
            currentYear = state.currentYear,
        )
    }
}
