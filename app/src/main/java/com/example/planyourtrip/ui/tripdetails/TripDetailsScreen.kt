package com.example.planyourtrip.ui.tripdetails

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ForeignAmount
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.categoryTotals
import com.example.planyourtrip.ui.common.components.BackTopAppBar
import com.example.planyourtrip.ui.common.components.BrandMark
import com.example.planyourtrip.ui.common.components.CurrencyPickerSheet
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.iconRes
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.common.delete.DeleteTripHost
import com.example.planyourtrip.ui.common.delete.DeleteTripViewModel
import com.example.planyourtrip.ui.common.share.ShareTripHost
import com.example.planyourtrip.ui.common.share.ShareTripViewModel
import com.example.planyourtrip.ui.common.tripStatus
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.tripdetails.components.ActivityCard
import com.example.planyourtrip.ui.tripdetails.components.AddActivityButton
import com.example.planyourtrip.ui.tripdetails.components.BudgetCard
import com.example.planyourtrip.ui.tripdetails.components.BudgetDialog
import com.example.planyourtrip.ui.tripdetails.components.CategoryChartCard
import com.example.planyourtrip.ui.tripdetails.components.CurrencyCard
import com.example.planyourtrip.ui.tripdetails.components.CurrencyCardActions
import com.example.planyourtrip.ui.tripdetails.components.DailyPlanProgress
import com.example.planyourtrip.ui.tripdetails.components.DayChipsRow
import com.example.planyourtrip.ui.tripdetails.components.DayPreviewCard
import com.example.planyourtrip.ui.tripdetails.components.DaySummaryCard
import com.example.planyourtrip.ui.tripdetails.components.ExpenseRow
import com.example.planyourtrip.ui.tripdetails.components.FeatureCard
import com.example.planyourtrip.ui.tripdetails.components.ItineraryActions
import com.example.planyourtrip.ui.tripdetails.components.ItineraryHeader
import com.example.planyourtrip.ui.tripdetails.components.OverviewMapIllustration
import com.example.planyourtrip.ui.tripdetails.components.PlanTimeline
import com.example.planyourtrip.ui.tripdetails.components.TripHeader
import com.example.planyourtrip.data.document.DocumentKind
import com.example.planyourtrip.data.document.DocumentType
import com.example.planyourtrip.data.trip.ChecklistItem
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.ui.tripdetails.prep.ChecklistRowState
import com.example.planyourtrip.ui.tripdetails.prep.ChecklistState
import com.example.planyourtrip.ui.tripdetails.prep.DocumentCardState
import com.example.planyourtrip.ui.tripdetails.prep.PrepActions
import com.example.planyourtrip.ui.tripdetails.prep.PrepDialogs
import com.example.planyourtrip.ui.tripdetails.prep.PrepUiState
import com.example.planyourtrip.ui.tripdetails.prep.PrepViewModel
import com.example.planyourtrip.ui.tripdetails.prep.dueState
import com.example.planyourtrip.ui.tripdetails.prep.itemsOf
import com.example.planyourtrip.ui.tripdetails.prep.listCount
import com.example.planyourtrip.ui.tripdetails.prep.prepProgress
import com.example.planyourtrip.ui.tripdetails.prep.prepTab
import com.example.planyourtrip.ui.tripdetails.prep.progressMessage
import com.example.planyourtrip.ui.tripdetails.prep.rememberPrepActions
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

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
    TripDetailsContent(
        state = state,
        selectedTab = selectedTab,
        budgetDialog = budgetDialog,
        currencyCard = currencyCard,
        currencyActions = currencyActions,
        itineraryActions = itineraryActions,
        onTabSelected = { selectedTab = it },
        onBack = onBack,
        onEditTrip = onEditTrip,
        onShareTrip = shareViewModel::onShareTrip,
        onDeleteTrip = deleteViewModel::onDeleteTrip,
        onEditBudget = viewModel::onEditBudget,
        onBudgetInputChange = viewModel::onBudgetInputChange,
        onSaveBudget = viewModel::onSaveBudget,
        onDismissBudget = viewModel::onDismissBudget,
        onAddExpense = onAddExpense,
        onExpenseClick = onExpenseClick,
        onOpenAssistant = onOpenAssistant,
        snackbarHostState = snackbarHostState,
        onOpenMaps = { destination -> openUrl(googleMapsSearchUrl(destination)) },
        prep = prepState,
        prepActions = prepActions,
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
    onTabSelected: (TripTab) -> Unit,
    onBack: () -> Unit,
    onEditTrip: () -> Unit,
    onEditBudget: () -> Unit,
    onBudgetInputChange: (String) -> Unit,
    onSaveBudget: () -> Unit,
    onDismissBudget: () -> Unit,
    onAddExpense: () -> Unit,
    onExpenseClick: (Long) -> Unit,
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
            // On every tab, above the navigation bar.
            floatingActionButton = { AssistantFab(onOpenAssistant) },
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
                                onTabSelected, onEditBudget, onOpenMaps,
                            )
                        TripTab.BUDGET -> budgetTab(state, onEditBudget, onAddExpense, onExpenseClick)
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
            onInputChange = onBudgetInputChange,
            onSave = onSaveBudget,
            onDismiss = onDismissBudget,
        )
    }
}

private val cardPadding = Modifier.padding(horizontal = Dimens.ScreenPadding)

private fun LazyListScope.overviewTab(
    state: TripDetailsUiState.Loaded,
    currencyCard: CurrencyCardState?,
    currencyActions: CurrencyCardActions,
    itineraryActions: ItineraryActions,
    onTabSelected: (TripTab) -> Unit,
    onSetBudget: () -> Unit,
    onOpenMaps: (String) -> Unit,
) {
    val itinerary = state.itinerary
    item(key = "budget") {
        BudgetCard(
            summary = state.summary,
            onSetBudget = onSetBudget,
            onClick = { onTabSelected(TripTab.BUDGET) },
            modifier = cardPadding.padding(top = 8.dp),
        )
    }
    if (currencyCard != null) {
        item(key = "currency") {
            CurrencyCard(currencyCard, currencyActions, modifier = cardPadding)
        }
    }
    item(key = "features") {
        // Equal-height cards side by side, as in the design.
        Row(
            modifier = cardPadding.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The map picture fills whatever height the Daily plan card next to it needs.
            FeatureCard(
                icon = R.drawable.ic_map,
                title = stringResource(R.string.trip_map),
                subtitle = state.trip.destination,
                subtitleMaxLines = 2,
                onClick = { onOpenMaps(state.trip.destination) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                content = {
                    OverviewMapIllustration(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                },
            )
            val progress = itinerary.progress
            val progressContent: (@Composable ColumnScope.() -> Unit)? =
                if (itinerary.hasActivities) ({ DailyPlanProgress(progress) }) else null
            FeatureCard(
                icon = R.drawable.ic_event_note,
                title = stringResource(R.string.trip_daily_plan),
                subtitle = if (itinerary.hasActivities) {
                    stringResource(R.string.plan_days_planned, progress.plannedDays, progress.totalDays)
                } else {
                    stringResource(R.string.trip_plan_empty)
                },
                onClick = { onTabSelected(TripTab.PLAN) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                content = progressContent,
            )
        }
    }
    // The day section appears once the trip has at least one activity.
    if (itinerary.hasActivities) {
        item(key = "day-preview") {
            DayPreviewCard(itinerary.overviewDay, itineraryActions, modifier = cardPadding)
        }
    }
}

private fun LazyListScope.planTab(
    state: TripDetailsUiState.Loaded,
    actions: ItineraryActions,
) {
    val itinerary = state.itinerary
    val plan = itinerary.plan
    item(key = "itinerary-header") {
        ItineraryHeader(state.trip, modifier = cardPadding.padding(top = 8.dp))
    }
    item(key = "day-chips") {
        DayChipsRow(itinerary.days, selected = plan.day, onSelect = actions.onPlanDaySelected)
    }
    item(key = "day-summary") {
        DaySummaryCard(plan, modifier = cardPadding)
    }
    if (plan.activities.isEmpty()) {
        item(key = "day-empty") {
            EmptyMessage(
                icon = R.drawable.ic_event_note,
                title = stringResource(R.string.itinerary_day_empty_title),
                body = stringResource(R.string.itinerary_day_empty_body),
            )
        }
    } else {
        // One item, so the timeline line runs unbroken between the cards.
        item(key = "timeline-${plan.day}") {
            PlanTimeline(plan.activities, actions, modifier = cardPadding)
        }
    }
    item(key = "add-activity") {
        AddActivityButton(onClick = { actions.onAddActivity(plan.day) }, modifier = cardPadding)
    }
    if (itinerary.outside.isNotEmpty()) {
        item(key = "outside-header") {
            Column(cardPadding.padding(top = 16.dp)) {
                Text(
                    text = stringResource(R.string.itinerary_outside_dates),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.itinerary_outside_dates_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(itinerary.outside, key = { "outside-${it.id}" }) { activity ->
            ActivityCard(
                activity,
                onClick = { actions.onActivityClick(activity.id) },
                showDate = true,
                onOpenInMaps = { actions.onOpenInMaps(activity) },
                modifier = cardPadding,
            )
        }
    }
}

private fun LazyListScope.budgetTab(
    state: TripDetailsUiState.Loaded,
    onEditBudget: () -> Unit,
    onAddExpense: () -> Unit,
    onExpenseClick: (Long) -> Unit,
) {
    item(key = "budget") {
        BudgetCard(
            summary = state.summary,
            onSetBudget = onEditBudget,
            showEditButton = true,
            modifier = cardPadding.padding(top = 8.dp),
        )
    }
    if (state.categoryTotals.isNotEmpty()) {
        item(key = "categories") {
            CategoryChartCard(state.categoryTotals, modifier = cardPadding)
        }
    }
    item(key = "expenses-header") {
        Column(cardPadding.padding(top = 8.dp)) {
            Text(
                text = stringResource(R.string.budget_expenses),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = stringResource(R.string.budget_add_expense),
                onClick = onAddExpense,
                leadingIcon = R.drawable.ic_add,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (state.expenses.isEmpty()) {
        item(key = "expenses-empty") {
            EmptyMessage(
                icon = R.drawable.ic_payments,
                title = stringResource(R.string.budget_no_expenses_title),
                body = stringResource(R.string.budget_no_expenses_body),
            )
        }
    } else {
        items(state.expenses, key = { "expense-${it.id}" }) { expense ->
            ExpenseRow(expense, onClick = { onExpenseClick(expense.id) }, modifier = cardPadding)
        }
    }
}

/** Centered icon, title and body text (empty states). */
@Composable
private fun EmptyMessage(@DrawableRes icon: Int, title: String, body: String) {
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

// ---------- Previews (sample data lives only here, never in the database) ----------

private val previewToday = LocalDate.of(2026, 10, 5)

private val previewTrip = Trip(
    id = 1,
    destination = "Rome, Italy",
    startDate = LocalDate.of(2026, 11, 12),
    endDate = LocalDate.of(2026, 11, 17),
    color = TripColor.SKY,
    budgetAgorot = 600_000,
)

private val previewExpenses = listOf(
    Expense(1, 1, "Flights TLV–FCO", 180_000, ExpenseCategory.FLIGHTS, LocalDate.of(2026, 9, 1)),
    Expense(2, 1, "Hotel Campo de' Fiori", 120_000, ExpenseCategory.HOTELS, LocalDate.of(2026, 9, 3)),
    Expense(3, 1, "Colosseum guided tour", 25_000, ExpenseCategory.ATTRACTIONS, LocalDate.of(2026, 9, 20)),
    Expense(4, 1, "Leonardo Express", 20_000, ExpenseCategory.TRANSPORT, LocalDate.of(2026, 9, 21)),
    // Entered in the trip currency: 10.00 EUR × 4.02 = ₪40.20.
    Expense(
        5, 1, "Gelato", 4_020, ExpenseCategory.FOOD, LocalDate.of(2026, 9, 22),
        ForeignAmount(BigDecimal("10.00"), "EUR", BigDecimal("4.02"), LocalDate.of(2026, 9, 22)),
    ),
    // A currency without decimals: 1500 JPY × 0.02512 = ₪37.68.
    Expense(
        6, 1, "Souvenirs", 3_768, ExpenseCategory.SHOPPING, LocalDate.of(2026, 9, 23),
        ForeignAmount(BigDecimal("1500"), "JPY", BigDecimal("0.02512"), LocalDate.of(2026, 9, 23)),
    ),
    Expense(7, 1, "SIM card", 5_900, ExpenseCategory.OTHER, LocalDate.of(2026, 9, 24)),
).sortedByDescending { it.date }

private val nov12 = LocalDate.of(2026, 11, 12)

private val previewActivities = listOf(
    TripActivity(
        1, 1, nov12, LocalTime.of(10, 0), "Colosseum", ActivityType.ACTIVITY,
        note = "Skip-the-line tour booked (voucher in email). Duration: 2.5h",
        location = "Piazza del Colosseo, 1", estimatedCostAgorot = 9_000, booked = true,
    ),
    TripActivity(
        2, 1, nov12, LocalTime.of(13, 0), "Lunch at Campo de' Fiori", ActivityType.DINING,
        note = "Try traditional Roman pinsa & suppli at Roscioli", estimatedCostAgorot = 12_000,
    ),
    TripActivity(
        3, 1, nov12, LocalTime.of(15, 30), "Pantheon", ActivityType.ACTIVITY,
        note = "Free entry before 17:00, audio guide recommended", location = "Piazza della Rotonda",
    ),
    TripActivity(
        4, 1, nov12, LocalTime.of(20, 0), "Dinner in Trastevere", ActivityType.DINING,
        note = "Reservation confirmed at Da Enzo al 29, table for 2", location = "Via dei Vascellari, 29",
        booked = true,
    ),
    TripActivity(
        9, 1, nov12, LocalTime.of(22, 30), "Taxi back to the hotel", ActivityType.TRANSPORT,
        estimatedCostAgorot = 6_000,
    ),
    TripActivity(10, 1, nov12, LocalTime.of(23, 0), "Hotel Campo de' Fiori", ActivityType.LODGING, booked = true),
    TripActivity(11, 1, nov12, null, "Buy a Roma Pass", ActivityType.OTHER, note = "At any metro station"),
    TripActivity(5, 1, nov12.plusDays(1), LocalTime.of(9, 0), "Vatican Museums", ActivityType.ACTIVITY),
    TripActivity(6, 1, nov12.plusDays(2), null, "Train to Florence", ActivityType.TRANSPORT),
    TripActivity(7, 1, nov12.plusDays(3), null, "Uffizi", ActivityType.ACTIVITY),
    TripActivity(8, 1, nov12.plusDays(4), null, "Check out", ActivityType.LODGING),
)

private fun previewState(
    trip: Trip = previewTrip,
    expenses: List<Expense> = previewExpenses,
    activities: List<TripActivity> = previewActivities,
    planDay: LocalDate? = null,
) = TripDetailsUiState.Loaded(
    trip = trip,
    status = tripStatus(trip, previewToday),
    summary = budgetSummary(trip.budgetAgorot, expenses, trip.startDate, trip.endDate, previewToday),
    categoryTotals = categoryTotals(expenses),
    expenses = expenses,
    itinerary = itineraryState(trip.startDate, trip.endDate, activities, planDay, null, previewToday),
)

private val previewChecklist = listOf(
    ChecklistItem(1, 1, ChecklistKind.BEFORE_TRIP, "Check passport is valid", checked = true, createdAt = 1),
    ChecklistItem(2, 1, ChecklistKind.BEFORE_TRIP, "Buy travel insurance", checked = true, createdAt = 2),
    ChecklistItem(3, 1, ChecklistKind.BEFORE_TRIP, "Book the hotel", checked = true, createdAt = 3),
    ChecklistItem(4, 1, ChecklistKind.BEFORE_TRIP, "Book Colosseum tickets", checked = true, createdAt = 4),
    ChecklistItem(5, 1, ChecklistKind.BEFORE_TRIP, "Tell the bank about the trip", checked = true, createdAt = 5),
    ChecklistItem(
        6, 1, ChecklistKind.BEFORE_TRIP, "Online check-in", dueDate = nov12.minusDays(1), createdAt = 6,
    ),
    ChecklistItem(7, 1, ChecklistKind.BEFORE_TRIP, "Exchange money", createdAt = 7),
    // Overdue (the preview's today is after 20 Sep).
    ChecklistItem(
        8, 1, ChecklistKind.BEFORE_TRIP, "Book airport transfer", dueDate = LocalDate.of(2026, 9, 20), createdAt = 8,
    ),
    ChecklistItem(9, 1, ChecklistKind.PACKING, "Phone charger", checked = true, createdAt = 9),
    ChecklistItem(10, 1, ChecklistKind.PACKING, "Power adapter", createdAt = 10),
)

private val previewDocuments = listOf(
    DocumentCardState(1, "Flight tickets", DocumentType.FLIGHT, "TLV → FCO · 12 Nov", DocumentKind.PDF),
    DocumentCardState(2, "Hotel booking", DocumentType.LODGING, "Hotel Campo de' Fiori · 5n", DocumentKind.PDF),
    DocumentCardState(3, "Travel insurance", DocumentType.INSURANCE, "Valid 12–17 Nov", DocumentKind.PHOTO),
)

private fun previewPrep(
    items: List<ChecklistItem> = previewChecklist,
    documents: List<DocumentCardState> = previewDocuments,
): PrepUiState {
    val progress = prepProgress(items)
    fun list(kind: ChecklistKind) = ChecklistState(
        kind = kind,
        rows = itemsOf(items, kind).map {
            ChecklistRowState(it.id, it.text, it.checked, it.dueDate, dueState(it, previewToday))
        },
        count = listCount(items, kind),
    )
    return PrepUiState.Loaded(
        destination = previewTrip.destination,
        startDate = previewTrip.startDate,
        endDate = previewTrip.endDate,
        progress = progress,
        message = progressMessage(progress, previewTrip.startDate, previewToday),
        beforeTrip = list(ChecklistKind.BEFORE_TRIP),
        packing = list(ChecklistKind.PACKING),
        documents = documents,
    )
}

private val previewPrep = previewPrep()

private val previewRate = CurrencyCardState.Rate("EUR", BigDecimal("3.4408"), LocalDate.of(2026, 10, 2))

private val previewCurrencyActions = CurrencyCardActions(onChooseCurrency = {}, onRetry = {})

private val previewItineraryActions = ItineraryActions(
    onPlanDaySelected = {}, onOverviewPreviousDay = {}, onOverviewNextDay = {}, onSeeFullPlan = {},
    onAddActivity = {}, onActivityClick = {},
)

@Composable
private fun Preview(
    state: TripDetailsUiState,
    tab: TripTab,
    darkTheme: Boolean = false,
    currency: CurrencyCardState = previewRate,
    prep: PrepUiState = previewPrep,
) {
    PlanYourTripTheme(darkTheme = darkTheme) {
        TripDetailsContent(
            state = state, selectedTab = tab, budgetDialog = null,
            currencyCard = currency, currencyActions = previewCurrencyActions,
            itineraryActions = previewItineraryActions,
            onTabSelected = {}, onBack = {}, onEditTrip = {}, onEditBudget = {}, onBudgetInputChange = {},
            onSaveBudget = {}, onDismissBudget = {}, onAddExpense = {}, onExpenseClick = {}, onOpenAssistant = {},
            prep = prep,
        )
    }
}

@Preview(name = "Overview", widthDp = 411, heightDp = 891)
@Composable
private fun OverviewPreview() = Preview(previewState(), TripTab.OVERVIEW)

@Preview(name = "Overview – no budget", widthDp = 411, heightDp = 891)
@Composable
private fun OverviewNoBudgetPreview() =
    Preview(
        previewState(previewTrip.copy(budgetAgorot = null), emptyList(), activities = emptyList()),
        TripTab.OVERVIEW,
        currency = CurrencyCardState.NoCurrency,
    )

@Preview(name = "Overview – shekel trip, dark", widthDp = 411, heightDp = 891)
@Composable
private fun OverviewShekelPreview() =
    Preview(previewState(), TripTab.OVERVIEW, darkTheme = true, currency = CurrencyCardState.Shekel)

@Preview(name = "Overview – rate unavailable", widthDp = 411, heightDp = 891)
@Composable
private fun OverviewRateUnavailablePreview() =
    Preview(previewState(), TripTab.OVERVIEW, currency = CurrencyCardState.Unavailable("EUR"))

@Preview(name = "Overview – JPY rate", widthDp = 411, heightDp = 891)
@Composable
private fun OverviewJpyPreview() = Preview(
    previewState(),
    TripTab.OVERVIEW,
    currency = CurrencyCardState.Rate("JPY", BigDecimal("0.01944"), LocalDate.of(2026, 10, 2)),
)

@Preview(name = "Budget", widthDp = 411, heightDp = 1900)
@Composable
private fun BudgetPreview() = Preview(previewState(), TripTab.BUDGET)

@Preview(name = "Budget – over budget, dark", widthDp = 411, heightDp = 1400)
@Composable
private fun BudgetOverPreview() =
    Preview(previewState(previewTrip.copy(budgetAgorot = 300_000)), TripTab.BUDGET, darkTheme = true)

@Preview(name = "Budget – dark", widthDp = 411, heightDp = 1900)
@Composable
private fun BudgetDarkPreview() = Preview(previewState(), TripTab.BUDGET, darkTheme = true)

@Preview(name = "Budget – narrow, large font", widthDp = 320, heightDp = 2400, fontScale = 1.5f)
@Composable
private fun BudgetNarrowPreview() = Preview(previewState(), TripTab.BUDGET)

@Preview(name = "Budget – no expenses", widthDp = 411, heightDp = 891)
@Composable
private fun BudgetEmptyPreview() = Preview(previewState(expenses = emptyList()), TripTab.BUDGET)

@Preview(name = "Overview – narrow, large font", widthDp = 320, heightDp = 1000, fontScale = 1.5f)
@Composable
private fun OverviewNarrowPreview() = Preview(
    previewState(previewTrip.copy(destination = "San Carlos de Bariloche, Argentina", budgetAgorot = 1_250_000)),
    TripTab.OVERVIEW,
)

@Preview(name = "Plan", widthDp = 411, heightDp = 2100)
@Composable
private fun PlanPreview() = Preview(previewState(), TripTab.PLAN)

@Preview(name = "Plan – all types, dark", widthDp = 411, heightDp = 2100)
@Composable
private fun PlanDarkPreview() = Preview(previewState(), TripTab.PLAN, darkTheme = true)

@Preview(name = "Overview – dark", widthDp = 411, heightDp = 1500)
@Composable
private fun OverviewDarkPreview() = Preview(previewState(), TripTab.OVERVIEW, darkTheme = true)

@Preview(name = "Plan – empty day, dark", widthDp = 411, heightDp = 1000)
@Composable
private fun PlanEmptyDayPreview() =
    Preview(previewState(planDay = nov12.plusDays(5)), TripTab.PLAN, darkTheme = true)

@Preview(name = "Prep", widthDp = 411, heightDp = 1700)
@Composable
private fun PrepPreview() = Preview(previewState(), TripTab.PREP)

@Preview(name = "Prep – dark", widthDp = 411, heightDp = 1700)
@Composable
private fun PrepDarkPreview() = Preview(previewState(), TripTab.PREP, darkTheme = true)

@Preview(name = "Prep – empty", widthDp = 411, heightDp = 1300)
@Composable
private fun PrepEmptyPreview() = Preview(previewState(), TripTab.PREP, prep = previewPrep(emptyList(), emptyList()))

@Preview(name = "Prep – narrow, large font", widthDp = 320, heightDp = 2200, fontScale = 1.5f)
@Composable
private fun PrepNarrowPreview() = Preview(previewState(), TripTab.PREP)

@Preview(name = "Plan – outside trip dates", widthDp = 411, heightDp = 1200)
@Composable
private fun PlanOutsidePreview() = Preview(
    previewState(
        // Dates moved one day later: the four activities of 12 Nov are now outside the trip.
        previewTrip.copy(startDate = nov12.plusDays(1), endDate = nov12.plusDays(6)),
    ),
    TripTab.PLAN,
)

@Preview(name = "Plan – narrow, large font", widthDp = 320, heightDp = 1600, fontScale = 1.5f)
@Composable
private fun PlanNarrowPreview() = Preview(previewState(), TripTab.PLAN)

@Preview(name = "Overview – long trip progress", widthDp = 411, heightDp = 1400)
@Composable
private fun OverviewLongTripPreview() =
    Preview(previewState(previewTrip.copy(endDate = nov12.plusDays(20))), TripTab.OVERVIEW)
