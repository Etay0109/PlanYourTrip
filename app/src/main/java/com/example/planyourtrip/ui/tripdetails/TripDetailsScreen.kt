package com.example.planyourtrip.ui.tripdetails

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.components.BackTopAppBar
import com.example.planyourtrip.ui.common.components.BrandMark
import com.example.planyourtrip.ui.common.components.CurrencyPickerSheet
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.tripStatus
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.tripdetails.components.BudgetCard
import com.example.planyourtrip.ui.tripdetails.components.BudgetDialog
import com.example.planyourtrip.ui.tripdetails.components.CategoryChartCard
import com.example.planyourtrip.ui.tripdetails.components.CurrencyCard
import com.example.planyourtrip.ui.tripdetails.components.CurrencyCardActions
import com.example.planyourtrip.ui.tripdetails.components.ExpenseRow
import com.example.planyourtrip.ui.tripdetails.components.FeatureCard
import com.example.planyourtrip.ui.tripdetails.components.TripHeader
import java.math.BigDecimal
import java.time.LocalDate

@Composable
fun TripDetailsScreen(
    onBack: () -> Unit,
    onEditTrip: () -> Unit,
    onAddExpense: () -> Unit,
    onExpenseClick: (Long) -> Unit,
    viewModel: TripDetailsViewModel = viewModel(factory = TripDetailsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val budgetDialog by viewModel.budgetDialog.collectAsStateWithLifecycle()
    val currencyCard by viewModel.currencyCard.collectAsStateWithLifecycle()
    val currencyPicker by viewModel.currencyPicker.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(TripTab.OVERVIEW) }

    // Opening the Overview refreshes a rate older than 12 hours; a changed currency is fetched right away.
    val loaded = state is TripDetailsUiState.Loaded
    val currencyCode = (state as? TripDetailsUiState.Loaded)?.trip?.currencyCode
    LaunchedEffect(selectedTab, loaded, currencyCode) {
        if (selectedTab == TripTab.OVERVIEW && loaded) viewModel.onOverviewShown()
    }

    val currencyActions = remember(viewModel) {
        CurrencyCardActions(
            onChooseCurrency = viewModel::onChooseCurrency,
            onRetry = viewModel::onRetryRate,
        )
    }
    TripDetailsContent(
        state = state,
        selectedTab = selectedTab,
        budgetDialog = budgetDialog,
        currencyCard = currencyCard,
        currencyActions = currencyActions,
        onTabSelected = { selectedTab = it },
        onBack = onBack,
        onEditTrip = onEditTrip,
        onEditBudget = viewModel::onEditBudget,
        onBudgetInputChange = viewModel::onBudgetInputChange,
        onSaveBudget = viewModel::onSaveBudget,
        onDismissBudget = viewModel::onDismissBudget,
        onAddExpense = onAddExpense,
        onExpenseClick = onExpenseClick,
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
}

@Composable
fun TripDetailsContent(
    state: TripDetailsUiState,
    selectedTab: TripTab,
    budgetDialog: BudgetDialogState?,
    currencyCard: CurrencyCardState?,
    currencyActions: CurrencyCardActions,
    onTabSelected: (TripTab) -> Unit,
    onBack: () -> Unit,
    onEditTrip: () -> Unit,
    onEditBudget: () -> Unit,
    onBudgetInputChange: (String) -> Unit,
    onSaveBudget: () -> Unit,
    onDismissBudget: () -> Unit,
    onAddExpense: () -> Unit,
    onExpenseClick: (Long) -> Unit,
) {
    when (state) {
        // Nothing is drawn until Room answers, so no placeholder flashes.
        TripDetailsUiState.Loading -> Scaffold(containerColor = MaterialTheme.colorScheme.background) {}
        TripDetailsUiState.NotFound -> NotFound(onBack)
        is TripDetailsUiState.Loaded -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // The header draws behind the status bar; the navigation bar pads itself.
            contentWindowInsets = WindowInsets(0),
            bottomBar = { TripNavigationBar(selectedTab, onTabSelected) },
        ) { innerPadding ->
            // A fresh list per tab, so a newly selected tab opens at the top.
            key(selectedTab) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "header") {
                        TripHeader(state.trip, state.status, onBack = onBack, onEdit = onEditTrip)
                    }
                    when (selectedTab) {
                        TripTab.OVERVIEW ->
                            overviewTab(state, currencyCard, currencyActions, onTabSelected, onEditBudget)
                        TripTab.BUDGET -> budgetTab(state, onEditBudget, onAddExpense, onExpenseClick)
                        TripTab.MAP -> comingSoon(R.drawable.ic_location, R.string.trip_coming_soon_map)
                        TripTab.PLAN -> comingSoon(R.drawable.ic_calendar, R.string.trip_coming_soon_plan)
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
    onTabSelected: (TripTab) -> Unit,
    onSetBudget: () -> Unit,
) {
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
            FeatureCard(
                icon = R.drawable.ic_bookmark,
                title = stringResource(R.string.trip_places),
                subtitle = stringResource(R.string.trip_places_empty),
                onClick = { onTabSelected(TripTab.MAP) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            FeatureCard(
                icon = R.drawable.ic_event_note,
                title = stringResource(R.string.trip_daily_plan),
                subtitle = stringResource(R.string.trip_plan_empty),
                onClick = { onTabSelected(TripTab.PLAN) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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

private fun LazyListScope.comingSoon(@DrawableRes icon: Int, @StringRes body: Int) {
    item(key = "coming-soon") {
        EmptyMessage(
            icon = icon,
            title = stringResource(R.string.trip_coming_soon_title),
            body = stringResource(body),
        )
    }
}

/** Centered icon, title and body text (empty states and "Coming soon"). */
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
    TripTab.MAP -> R.drawable.ic_location
    TripTab.PLAN -> R.drawable.ic_calendar
}

@StringRes
private fun TripTab.labelRes(): Int = when (this) {
    TripTab.OVERVIEW -> R.string.trip_tab_overview
    TripTab.BUDGET -> R.string.trip_tab_budget
    TripTab.MAP -> R.string.trip_tab_map
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
).sortedByDescending { it.date }

private fun previewState(trip: Trip = previewTrip, expenses: List<Expense> = previewExpenses) =
    TripDetailsUiState.Loaded(
        trip = trip,
        status = tripStatus(trip, previewToday),
        summary = budgetSummary(trip.budgetAgorot, expenses, trip.startDate, trip.endDate, previewToday),
        categoryTotals = categoryTotals(expenses),
        expenses = expenses,
    )

private val previewRate = CurrencyCardState.Rate("EUR", BigDecimal("3.4408"), LocalDate.of(2026, 10, 2))

private val previewCurrencyActions = CurrencyCardActions(onChooseCurrency = {}, onRetry = {})

@Composable
private fun Preview(
    state: TripDetailsUiState,
    tab: TripTab,
    darkTheme: Boolean = false,
    currency: CurrencyCardState = previewRate,
) {
    PlanYourTripTheme(darkTheme = darkTheme) {
        TripDetailsContent(
            state = state, selectedTab = tab, budgetDialog = null,
            currencyCard = currency, currencyActions = previewCurrencyActions,
            onTabSelected = {}, onBack = {}, onEditTrip = {}, onEditBudget = {}, onBudgetInputChange = {},
            onSaveBudget = {}, onDismissBudget = {}, onAddExpense = {}, onExpenseClick = {},
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
        previewState(previewTrip.copy(budgetAgorot = null), emptyList()),
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

@Preview(name = "Budget", widthDp = 411, heightDp = 1400)
@Composable
private fun BudgetPreview() = Preview(previewState(), TripTab.BUDGET)

@Preview(name = "Budget – over budget, dark", widthDp = 411, heightDp = 1400)
@Composable
private fun BudgetOverPreview() =
    Preview(previewState(previewTrip.copy(budgetAgorot = 300_000)), TripTab.BUDGET, darkTheme = true)

@Preview(name = "Budget – no expenses", widthDp = 411, heightDp = 891)
@Composable
private fun BudgetEmptyPreview() = Preview(previewState(expenses = emptyList()), TripTab.BUDGET)

@Preview(name = "Overview – narrow, large font", widthDp = 320, heightDp = 1000, fontScale = 1.5f)
@Composable
private fun OverviewNarrowPreview() = Preview(
    previewState(previewTrip.copy(destination = "San Carlos de Bariloche, Argentina", budgetAgorot = 1_250_000)),
    TripTab.OVERVIEW,
)

@Preview(name = "Map – coming soon", widthDp = 411, heightDp = 891)
@Composable
private fun MapPreview() = Preview(previewState(), TripTab.MAP)
