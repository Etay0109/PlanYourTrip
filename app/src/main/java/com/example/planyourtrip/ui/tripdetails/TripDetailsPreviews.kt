package com.example.planyourtrip.ui.tripdetails

import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.example.planyourtrip.data.document.DocumentKind
import com.example.planyourtrip.data.document.DocumentType
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.ChecklistItem
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ForeignAmount
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.categoryTotals
import com.example.planyourtrip.ui.common.split.balances
import com.example.planyourtrip.ui.common.split.myShare
import com.example.planyourtrip.ui.common.split.sharedExpenseCount
import com.example.planyourtrip.ui.common.split.simplifyTransfers
import com.example.planyourtrip.ui.common.split.travelerOrder
import com.example.planyourtrip.ui.common.tripStatus
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.tripdetails.components.CurrencyCardActions
import com.example.planyourtrip.ui.tripdetails.components.ItineraryActions
import com.example.planyourtrip.ui.tripdetails.prep.ChecklistRowState
import com.example.planyourtrip.ui.tripdetails.prep.ChecklistState
import com.example.planyourtrip.ui.tripdetails.prep.DocumentCardState
import com.example.planyourtrip.ui.tripdetails.prep.PrepUiState
import com.example.planyourtrip.ui.tripdetails.prep.dueState
import com.example.planyourtrip.ui.tripdetails.prep.itemsOf
import com.example.planyourtrip.ui.tripdetails.prep.listCount
import com.example.planyourtrip.ui.tripdetails.prep.prepProgress
import com.example.planyourtrip.ui.tripdetails.prep.progressMessage
import com.example.planyourtrip.ui.tripdetails.split.SplitUiState
import com.example.planyourtrip.ui.tripdetails.split.SplitViewModel
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

// ---------- Previews (sample data lives only here, never in the database) ----------

private val previewToday = LocalDate.of(2026, 10, 5)

private val previewTrip = Trip(
    id = 1,
    destination = "Rome, Italy",
    startDate = LocalDate.of(2026, 11, 12),
    endDate = LocalDate.of(2026, 11, 17),
    color = TripColor.SKY,
    budgetAgorot = 600_000,
    ownerId = PREVIEW_ME,
    memberIds = listOf(PREVIEW_ME),
)

private val previewTravelers = listOf(
    Traveler(1, 1, "noa", "Noa", TravelerColor.ROSE),
    Traveler(2, 1, "amit", "Amit", TravelerColor.INDIGO),
)

/** The signed-in member's uid in the previews ("You"). */
private const val PREVIEW_ME = "me-uid"

private val mine = listOf(PREVIEW_ME)

private val everyone = listOf(PREVIEW_ME, "noa", "amit")

private val previewExpenses = listOf(
    Expense(
        1, 1, "Flights TLV–FCO", 180_000, ExpenseCategory.FLIGHTS, LocalDate.of(2026, 9, 1),
        paidBy = PREVIEW_ME, splitBetween = mine,
    ),
    // Paid by Noa for everyone (3 ways), and by me for Noa only.
    Expense(
        2, 1, "Hotel Campo de' Fiori", 120_000, ExpenseCategory.HOTELS, LocalDate.of(2026, 9, 3),
        paidBy = "noa", splitBetween = everyone,
    ),
    Expense(
        3, 1, "Colosseum guided tour", 25_000, ExpenseCategory.ATTRACTIONS, LocalDate.of(2026, 9, 20),
        paidBy = PREVIEW_ME, splitBetween = listOf("noa"),
    ),
    Expense(
        4, 1, "Leonardo Express", 20_000, ExpenseCategory.TRANSPORT, LocalDate.of(2026, 9, 21),
        paidBy = "amit", splitBetween = everyone,
    ),
    // Entered in the trip currency: 10.00 EUR × 4.02 = ₪40.20.
    Expense(
        5, 1, "Gelato", 4_020, ExpenseCategory.FOOD, LocalDate.of(2026, 9, 22),
        ForeignAmount(BigDecimal("10.00"), "EUR", BigDecimal("4.02"), LocalDate.of(2026, 9, 22)),
        paidBy = PREVIEW_ME, splitBetween = mine,
    ),
    // A currency without decimals: 1500 JPY × 0.02512 = ₪37.68.
    Expense(
        6, 1, "Souvenirs", 3_768, ExpenseCategory.SHOPPING, LocalDate.of(2026, 9, 23),
        ForeignAmount(BigDecimal("1500"), "JPY", BigDecimal("0.02512"), LocalDate.of(2026, 9, 23)),
        paidBy = PREVIEW_ME, splitBetween = mine,
    ),
    Expense(
        7, 1, "SIM card", 5_900, ExpenseCategory.OTHER, LocalDate.of(2026, 9, 24),
        paidBy = PREVIEW_ME, splitBetween = mine,
    ),
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
    travelers: List<Traveler> = previewTravelers,
): TripDetailsUiState.Loaded {
    val order = travelerOrder(PREVIEW_ME, trip.memberIds, travelers)
    // Without travelers every expense is mine alone.
    val shown =
        if (travelers.isEmpty()) expenses.map { it.copy(paidBy = PREVIEW_ME, splitBetween = mine) } else expenses
    val myShares = shown.associate { it.id to it.myShare(order, PREVIEW_ME) }
    return TripDetailsUiState.Loaded(
        trip = trip,
        status = tripStatus(trip, previewToday),
        summary = budgetSummary(
            trip.budgetAgorot, myShares.values.sum(), trip.startDate, trip.endDate, previewToday,
        ),
        categoryTotals = categoryTotals(shown, { myShares.getValue(it.id) }, includeZeroCategories = true),
        expenses = shown,
        travelers = travelers,
        myShares = myShares,
        expenseSplits = expenseSplits(shown, travelers, myShares, PREVIEW_ME, order),
        itinerary = itineraryState(trip.startDate, trip.endDate, activities, planDay, null, previewToday),
    )
}

/** The split section for [state], computed like SplitViewModel does. */
private fun previewSplit(state: TripDetailsUiState.Loaded, settlements: List<Settlement> = emptyList()): SplitUiState {
    val order = travelerOrder(PREVIEW_ME, state.trip.memberIds, state.travelers)
    return SplitUiState.Loaded(
        me = PREVIEW_ME,
        travelers = state.travelers,
        transfers = simplifyTransfers(balances(state.expenses, settlements, order), order, PREVIEW_ME),
        sharedExpenseCount = sharedExpenseCount(state.expenses),
        settlements = settlements,
    )
}

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
    split: SplitUiState = (state as? TripDetailsUiState.Loaded)?.let { previewSplit(it) } ?: SplitUiState.Loading,
) {
    PlanYourTripTheme(darkTheme = darkTheme) {
        TripDetailsContent(
            state = state, selectedTab = tab, budgetDialog = null,
            currencyCard = currency, currencyActions = previewCurrencyActions,
            itineraryActions = previewItineraryActions, budgetActions = BudgetActions.None,
            onTabSelected = {}, onBack = {}, onEditTrip = {}, onOpenAssistant = {},
            prep = prep,
            split = split,
        )
    }
}

@Preview(name = "Overview", widthDp = 411, heightDp = 891)
@Composable
internal fun OverviewPreview() = Preview(previewState(), TripTab.OVERVIEW)

@Preview(name = "Overview – no budget", widthDp = 411, heightDp = 891)
@Composable
internal fun OverviewNoBudgetPreview() =
    Preview(
        previewState(previewTrip.copy(budgetAgorot = null), emptyList(), activities = emptyList()),
        TripTab.OVERVIEW,
        currency = CurrencyCardState.NoCurrency,
    )

@Preview(name = "Overview – shekel trip, dark", widthDp = 411, heightDp = 891)
@Composable
internal fun OverviewShekelPreview() =
    Preview(previewState(), TripTab.OVERVIEW, darkTheme = true, currency = CurrencyCardState.Shekel)

@Preview(name = "Overview – rate unavailable", widthDp = 411, heightDp = 891)
@Composable
internal fun OverviewRateUnavailablePreview() =
    Preview(previewState(), TripTab.OVERVIEW, currency = CurrencyCardState.Unavailable("EUR"))

@Preview(name = "Overview – JPY rate", widthDp = 411, heightDp = 891)
@Composable
internal fun OverviewJpyPreview() = Preview(
    previewState(),
    TripTab.OVERVIEW,
    currency = CurrencyCardState.Rate("JPY", BigDecimal("0.01944"), LocalDate.of(2026, 10, 2)),
)

@Preview(name = "Budget", widthDp = 411, heightDp = 1900)
@Composable
internal fun BudgetPreview() = Preview(previewState(), TripTab.BUDGET)

@Preview(name = "Budget – over budget, dark", widthDp = 411, heightDp = 1400)
@Composable
internal fun BudgetOverPreview() =
    Preview(previewState(previewTrip.copy(budgetAgorot = 300_000)), TripTab.BUDGET, darkTheme = true)

@Preview(name = "Budget – dark", widthDp = 411, heightDp = 1900)
@Composable
internal fun BudgetDarkPreview() = Preview(previewState(), TripTab.BUDGET, darkTheme = true)

@Preview(name = "Budget – narrow, large font", widthDp = 320, heightDp = 2400, fontScale = 1.5f)
@Composable
internal fun BudgetNarrowPreview() = Preview(previewState(), TripTab.BUDGET)

@Preview(name = "Budget – no expenses", widthDp = 411, heightDp = 891)
@Composable
internal fun BudgetEmptyPreview() = Preview(previewState(expenses = emptyList()), TripTab.BUDGET)

@Preview(name = "Budget – only me", widthDp = 411, heightDp = 1900)
@Composable
internal fun BudgetOnlyMePreview() = Preview(previewState(travelers = emptyList()), TripTab.BUDGET)

@Preview(name = "Budget – all settled up", widthDp = 411, heightDp = 1900)
@Composable
internal fun BudgetSettledPreview() {
    val state = previewState()
    val order = travelerOrder(PREVIEW_ME, state.trip.memberIds, state.travelers)
    // Pay every open debt.
    val transfers = simplifyTransfers(balances(state.expenses, emptyList(), order), order, PREVIEW_ME)
    val settlements = transfers.mapIndexed { i, t ->
        Settlement(i + 1L, 1, "s$i", t.fromKey, t.toKey, t.amountAgorot, LocalDate.of(2026, 9, 30))
    }
    Preview(state, TripTab.BUDGET, split = previewSplit(state, settlements))
}

@Preview(name = "Overview – narrow, large font", widthDp = 320, heightDp = 1000, fontScale = 1.5f)
@Composable
internal fun OverviewNarrowPreview() = Preview(
    previewState(previewTrip.copy(destination = "San Carlos de Bariloche, Argentina", budgetAgorot = 1_250_000)),
    TripTab.OVERVIEW,
)

@Preview(name = "Plan", widthDp = 411, heightDp = 2100)
@Composable
internal fun PlanPreview() = Preview(previewState(), TripTab.PLAN)

@Preview(name = "Plan – all types, dark", widthDp = 411, heightDp = 2100)
@Composable
internal fun PlanDarkPreview() = Preview(previewState(), TripTab.PLAN, darkTheme = true)

@Preview(name = "Overview – dark", widthDp = 411, heightDp = 1500)
@Composable
internal fun OverviewDarkPreview() = Preview(previewState(), TripTab.OVERVIEW, darkTheme = true)

@Preview(name = "Plan – empty day, dark", widthDp = 411, heightDp = 1000)
@Composable
internal fun PlanEmptyDayPreview() =
    Preview(previewState(planDay = nov12.plusDays(5)), TripTab.PLAN, darkTheme = true)

@Preview(name = "Prep", widthDp = 411, heightDp = 1700)
@Composable
internal fun PrepPreview() = Preview(previewState(), TripTab.PREP)

@Preview(name = "Prep – dark", widthDp = 411, heightDp = 1700)
@Composable
internal fun PrepDarkPreview() = Preview(previewState(), TripTab.PREP, darkTheme = true)

@Preview(name = "Prep – empty", widthDp = 411, heightDp = 1300)
@Composable
internal fun PrepEmptyPreview() = Preview(previewState(), TripTab.PREP, prep = previewPrep(emptyList(), emptyList()))

@Preview(name = "Prep – narrow, large font", widthDp = 320, heightDp = 2200, fontScale = 1.5f)
@Composable
internal fun PrepNarrowPreview() = Preview(previewState(), TripTab.PREP)

@Preview(name = "Plan – outside trip dates", widthDp = 411, heightDp = 1200)
@Composable
internal fun PlanOutsidePreview() = Preview(
    previewState(
        // Dates moved one day later: the four activities of 12 Nov are now outside the trip.
        previewTrip.copy(startDate = nov12.plusDays(1), endDate = nov12.plusDays(6)),
    ),
    TripTab.PLAN,
)

@Preview(name = "Plan – narrow, large font", widthDp = 320, heightDp = 1600, fontScale = 1.5f)
@Composable
internal fun PlanNarrowPreview() = Preview(previewState(), TripTab.PLAN)

@Preview(name = "Overview – long trip progress", widthDp = 411, heightDp = 1400)
@Composable
internal fun OverviewLongTripPreview() =
    Preview(previewState(previewTrip.copy(endDate = nov12.plusDays(20))), TripTab.OVERVIEW)
