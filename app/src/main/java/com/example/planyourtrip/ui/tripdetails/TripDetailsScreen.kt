package com.example.planyourtrip.ui.tripdetails

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.components.BackTopAppBar
import com.example.planyourtrip.ui.common.components.BrandMark
import com.example.planyourtrip.ui.common.components.CurrencyPickerSheet
import com.example.planyourtrip.ui.common.delete.DeleteTripHost
import com.example.planyourtrip.ui.common.delete.DeleteTripViewModel
import com.example.planyourtrip.ui.common.iconRes
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.common.share.ShareTripHost
import com.example.planyourtrip.ui.common.share.ShareTripViewModel
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.tripdetails.components.BudgetDialog
import com.example.planyourtrip.ui.tripdetails.components.CurrencyCardActions
import com.example.planyourtrip.ui.tripdetails.components.ItineraryActions
import com.example.planyourtrip.ui.tripdetails.components.TripHeader
import com.example.planyourtrip.ui.tripdetails.prep.PrepActions
import com.example.planyourtrip.ui.tripdetails.prep.PrepDialogs
import com.example.planyourtrip.ui.tripdetails.prep.PrepUiState
import com.example.planyourtrip.ui.tripdetails.prep.PrepViewModel
import com.example.planyourtrip.ui.tripdetails.prep.prepTab
import com.example.planyourtrip.ui.tripdetails.prep.rememberPrepActions
import com.example.planyourtrip.ui.tripdetails.split.SplitActions
import com.example.planyourtrip.ui.tripdetails.split.SplitDialogs
import com.example.planyourtrip.ui.tripdetails.split.SplitUiState
import com.example.planyourtrip.ui.tripdetails.split.SplitViewModel
import kotlinx.coroutines.launch
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
    viewModel: TripDetailsViewModel = viewModel(factory = TripDetailsViewModel.Factory),
    shareViewModel: ShareTripViewModel = viewModel(factory = ShareTripViewModel.Factory),
    deleteViewModel: DeleteTripViewModel = viewModel(factory = DeleteTripViewModel.Factory),
    prepViewModel: PrepViewModel = viewModel(factory = PrepViewModel.Factory),
    splitViewModel: SplitViewModel = viewModel(factory = SplitViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val budgetDialog by viewModel.budgetDialog.collectAsStateWithLifecycle()
    val currencyCard by viewModel.currencyCard.collectAsStateWithLifecycle()
    val currencyPicker by viewModel.currencyPicker.collectAsStateWithLifecycle()
    val deletedActivity by viewModel.deletedActivity.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // "Activity deleted" with "Undo", once per delete; Undo brings the activity back.
    val deletedMessage = stringResource(R.string.activity_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(deletedActivity) {
        val deleted = deletedActivity ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.onUndoDelete(deleted)
        } else {
            viewModel.onDeleteMessageDismissed(deleted)
        }
    }
    var selectedTab by rememberSaveable { mutableStateOf(TripTab.OVERVIEW) }

    // Opening the Overview refreshes a rate older than 12 hours; a changed currency is fetched right away.
    val loaded = state is TripDetailsUiState.Loaded
    val currencyCode = (state as? TripDetailsUiState.Loaded)?.trip?.currencyCode
    LaunchedEffect(selectedTab, loaded, currencyCode) {
        if (selectedTab == TripTab.OVERVIEW && loaded) viewModel.onOverviewShown()
    }

    // The map card (destination) and the Plan cards (activity location) open Google Maps: the app, or the browser;
    // nothing at all → a message.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noMapsApp = stringResource(R.string.trip_no_maps_app)
    val openUrl: (String) -> Unit = remember(context, snackbarHostState, noMapsApp) {
        { url -> if (!openGoogleMaps(context, url)) scope.launch { snackbarHostState.showSnackbar(noMapsApp) } }
    }
    val destination = (state as? TripDetailsUiState.Loaded)?.trip?.destination.orEmpty()

    val prepState by prepViewModel.uiState.collectAsStateWithLifecycle()
    val prepActions = rememberPrepActions(prepViewModel, snackbarHostState)

    val splitState by splitViewModel.uiState.collectAsStateWithLifecycle()
    val splitActions = remember(splitViewModel) {
        SplitActions(
            onAddTraveler = splitViewModel::onAddTraveler,
            onTravelerClick = splitViewModel::onTravelerClick,
            onSettleUp = splitViewModel::onSettleUp,
            onShowHistory = splitViewModel::onShowHistory,
        )
    }

    val currencyActions = remember(viewModel) {
        CurrencyCardActions(
            onChooseCurrency = viewModel::onChooseCurrency,
            onRetry = viewModel::onRetryRate,
        )
    }
    val itineraryActions = remember(viewModel, onAddActivity, onActivityClick, openUrl, destination) {
        ItineraryActions(
            onPlanDaySelected = viewModel::onPlanDaySelected,
            onOverviewPreviousDay = viewModel::onOverviewPreviousDay,
            onOverviewNextDay = viewModel::onOverviewNextDay,
            onSeeFullPlan = {
                viewModel.onSeeFullPlan()
                selectedTab = TripTab.PLAN
            },
            onAddActivity = onAddActivity,
            onActivityClick = onActivityClick,
            onOpenInMaps = { activity ->
                activity.location?.let { openUrl(googleMapsActivityUrl(it, destination)) }
            },
        )
    }
    val budgetActions = remember(viewModel, onAddExpense, onExpenseClick) {
        BudgetActions(
            onEditBudget = viewModel::onEditBudget,
            onBudgetInputChange = viewModel::onBudgetInputChange,
            onSaveBudget = viewModel::onSaveBudget,
            onDismissBudget = viewModel::onDismissBudget,
            onAddExpense = onAddExpense,
            onExpenseClick = onExpenseClick,
        )
    }
    TripDetailsContent(
        state = state,
        selectedTab = selectedTab,
        budgetDialog = budgetDialog,
        currencyCard = currencyCard,
        currencyActions = currencyActions,
        itineraryActions = itineraryActions,
        budgetActions = budgetActions,
        onTabSelected = { selectedTab = it },
        onBack = onBack,
        onEditTrip = onEditTrip,
        onShareTrip = shareViewModel::onShareTrip,
        onDeleteTrip = deleteViewModel::onDeleteTrip,
        onOpenAssistant = onOpenAssistant,
        snackbarHostState = snackbarHostState,
        onOpenMaps = { destination -> openUrl(googleMapsSearchUrl(destination)) },
        prep = prepState,
        prepActions = prepActions,
        split = splitState,
        splitActions = splitActions,
    )

    currencyPicker?.let { picker ->
        CurrencyPickerSheet(
            state = picker,
            onQueryChange = viewModel::onCurrencyQueryChange,
            onSelect = viewModel::onCurrencySelected,
            onRetry = viewModel::onRetryCurrencies,
            onDismiss = viewModel::onDismissCurrencyPicker,
        )
    }
    ShareTripHost(shareViewModel)
    // Confirmed here, deleted on Home (the trip screen would otherwise show "not found" for a moment).
    DeleteTripHost(deleteViewModel, onConfirm = { deleteViewModel.takeConfirmed()?.let(onTripDeleted) })
    PrepDialogs(prepViewModel)
    SplitDialogs(splitViewModel)
}

/** Opens a Google Maps [url]; false when no app (not even a browser) can open it. */
private fun openGoogleMaps(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (e: ActivityNotFoundException) {
    false
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
    /** "Delete trip" in the header menu: (trip id, destination). */
    onDeleteTrip: (Long, String) -> Unit = { _, _ -> },
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
            floatingActionButton = {
                if (selectedTab == TripTab.BUDGET) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        AddExpenseFab(budgetActions.onAddExpense)
                        Spacer(Modifier.weight(1f).widthIn(min = 16.dp))
                        AssistantFab(onOpenAssistant)
                    }
                } else {
                    AssistantFab(onOpenAssistant)
                }
            },
            // Centered = the full-width row above lines the two buttons up with both edges.
            floatingActionButtonPosition = if (selectedTab == TripTab.BUDGET) FabPosition.Center else FabPosition.End,
        ) { innerPadding ->
            // A fresh list per tab, so a newly selected tab opens at the top.
            key(selectedTab) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Extra room at the end so the assistant button never covers the last card.
                    contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + FabClearance),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "header") {
                        TripHeader(
                            state.trip,
                            state.status,
                            onBack = onBack,
                            onEdit = onEditTrip,
                            onShare = { onShareTrip(state.trip.id) },
                            onDelete = { onDeleteTrip(state.trip.id, state.trip.destination) },
                        )
                    }
                    when (selectedTab) {
                        TripTab.OVERVIEW ->
                            overviewTab(
                                state, currencyCard, currencyActions, itineraryActions,
                                onTabSelected, budgetActions.onEditBudget, onOpenMaps,
                            )
                        TripTab.BUDGET -> budgetTab(state, split, splitActions, budgetActions)
                        TripTab.PREP -> prepTab(prep, prepActions)
                        TripTab.PLAN -> planTab(state, itineraryActions)
                    }
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

/** Side padding of the trip screen's cards. */
internal val cardPadding = Modifier.padding(horizontal = Dimens.ScreenPadding)

/** Centered icon, title and body text (empty states). */
@Composable
internal fun EmptyMessage(@DrawableRes icon: Int, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenPadding + 12.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Bottom list padding that keeps the last card clear of the assistant button. */
private val FabClearance = 96.dp

/** Round button with a sparkle that opens the trip assistant. */
@Composable
private fun AssistantFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
    ) {
        Icon(painterResource(R.drawable.ic_sparkle), contentDescription = stringResource(R.string.assistant_open))
    }
}

@Composable
private fun TripNavigationBar(selected: TripTab, onSelect: (TripTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        TripTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.iconRes()), contentDescription = null) },
                label = { Text(stringResource(tab.labelRes()), maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@DrawableRes
private fun TripTab.iconRes(): Int = when (this) {
    TripTab.OVERVIEW -> R.drawable.ic_dashboard
    TripTab.BUDGET -> R.drawable.ic_payments
    TripTab.PREP -> R.drawable.ic_checklist
    TripTab.PLAN -> R.drawable.ic_calendar
}

@StringRes
private fun TripTab.labelRes(): Int = when (this) {
    TripTab.OVERVIEW -> R.string.trip_tab_overview
    TripTab.BUDGET -> R.string.trip_tab_budget
    TripTab.PREP -> R.string.trip_tab_prep
    TripTab.PLAN -> R.string.trip_tab_plan
}

@Composable
private fun NotFound(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopAppBar(onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            BrandMark(size = 96.dp, iconSize = 44.dp)
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.trip_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
