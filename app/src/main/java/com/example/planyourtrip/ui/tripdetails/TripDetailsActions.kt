package com.example.planyourtrip.ui.tripdetails

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.ui.tripdetails.components.CurrencyCardActions
import com.example.planyourtrip.ui.tripdetails.components.ItineraryActions
import com.example.planyourtrip.ui.tripdetails.split.SplitActions
import com.example.planyourtrip.ui.tripdetails.split.SplitViewModel
import java.time.LocalDate

@Composable
internal fun rememberSplitActions(splitViewModel: SplitViewModel): SplitActions =
remember(splitViewModel) {
    SplitActions(
        onAddTraveler = splitViewModel::onAddTraveler,
        onTravelerClick = splitViewModel::onTravelerClick,
        onSettleUp = splitViewModel::onSettleUp,
        onShowHistory = splitViewModel::onShowHistory,
        onMerge = splitViewModel::onMergeSuggestion,
        onKeepSeparate = splitViewModel::onDismissSuggestion,
    )
}

@Composable
internal fun rememberCurrencyActions(viewModel: TripDetailsViewModel): CurrencyCardActions =
remember(viewModel) {
    CurrencyCardActions(
        onChooseCurrency = viewModel::onChooseCurrency,
        onRetry = viewModel::onRetryRate,
    )
}

/** "See full plan" ([onSeeFullPlan]) also switches to the Plan tab; "Open in Maps" searches the activity's place. */
@Composable
internal fun rememberItineraryActions(
    viewModel: TripDetailsViewModel,
    onAddActivity: (LocalDate) -> Unit,
    onActivityClick: (Long) -> Unit,
    openUrl: (String) -> Unit,
    destination: String,
    onSeeFullPlan: () -> Unit,
): ItineraryActions =
remember(viewModel, onAddActivity, onActivityClick, openUrl, destination) {
    ItineraryActions(
        onPlanDaySelected = viewModel::onPlanDaySelected,
        onOverviewPreviousDay = viewModel::onOverviewPreviousDay,
        onOverviewNextDay = viewModel::onOverviewNextDay,
        onSeeFullPlan = {
            viewModel.onSeeFullPlan()
            onSeeFullPlan()
        },
        onAddActivity = onAddActivity,
        onActivityClick = onActivityClick,
        onOpenInMaps = { activity ->
            activity.location?.let { openUrl(googleMapsActivityUrl(it, destination)) }
        },
    )
}

@Composable
internal fun rememberBudgetActions(
    viewModel: TripDetailsViewModel,
    onAddExpense: () -> Unit,
    onExpenseClick: (Long) -> Unit,
): BudgetActions =
remember(viewModel, onAddExpense, onExpenseClick) {
    BudgetActions(
        onEditBudget = viewModel::onEditBudget,
        onBudgetInputChange = viewModel::onBudgetInputChange,
        onSaveBudget = viewModel::onSaveBudget,
        onDismissBudget = viewModel::onDismissBudget,
        onAddExpense = onAddExpense,
        onExpenseClick = onExpenseClick,
    )
}
