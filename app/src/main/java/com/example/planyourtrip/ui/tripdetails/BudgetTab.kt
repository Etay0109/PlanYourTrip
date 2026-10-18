package com.example.planyourtrip.ui.tripdetails

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.categoryTotals
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.tripdetails.components.BudgetCard
import com.example.planyourtrip.ui.tripdetails.components.CategoryChartCard
import com.example.planyourtrip.ui.tripdetails.components.ExpenseRow
import com.example.planyourtrip.ui.tripdetails.split.MergeSuggestionCard
import com.example.planyourtrip.ui.tripdetails.split.SplitActions
import com.example.planyourtrip.ui.tripdetails.split.SplitUiState
import com.example.planyourtrip.ui.tripdetails.split.TravelersSection
import com.example.planyourtrip.ui.tripdetails.split.WhoOwesWhomCard

/** Callbacks of the budget card, the "Set budget" dialog and the expense list (Overview and Budget tabs). */
class BudgetActions(
    val onEditBudget: () -> Unit,
    val onBudgetInputChange: (String) -> Unit,
    val onSaveBudget: () -> Unit,
    val onDismissBudget: () -> Unit,
    val onAddExpense: () -> Unit,
    val onExpenseClick: (Long) -> Unit,
) {
    companion object {
        val None = BudgetActions({}, {}, {}, {}, {}, {})
    }
}

internal fun LazyListScope.budgetTab(
    state: TripDetailsUiState.Loaded,
    split: SplitUiState,
    splitActions: SplitActions,
    actions: BudgetActions,
) {
    item(key = "budget") {
        BudgetCard(
            summary = state.summary,
            onSetBudget = actions.onEditBudget,
            showEditButton = true,
            modifier = cardPadding.padding(top = Spacing.s),
        )
    }
    if (split is SplitUiState.Loaded) {
        split.suggestion?.let { suggestion ->
            item(key = "merge-${suggestion.traveler.uuid}") {
                MergeSuggestionCard(
                    suggestion = suggestion,
                    isMerging = split.isMerging,
                    actions = splitActions,
                    modifier = cardPadding.padding(top = Spacing.s),
                )
            }
        }
        item(key = "travelers") {
            TravelersSection(split, splitActions, modifier = Modifier.padding(top = Spacing.s))
        }
        // Only me: nobody to owe or be owed.
        if (split.hasOthers) {
            item(key = "owes") {
                WhoOwesWhomCard(split, splitActions, modifier = cardPadding)
            }
        }
    }
    if (state.categoryTotals.isNotEmpty()) {
        item(key = "categories") {
            CategoryChartCard(state.categoryTotals, modifier = cardPadding)
        }
    }
    item(key = "expenses-header") {
        Row(cardPadding.padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.budget_expenses),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            if (state.expenses.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.budget_newest_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            ExpenseRow(
                expense,
                onClick = { actions.onExpenseClick(expense.id) },
                modifier = cardPadding,
                // With other travelers: who paid, who shares it and my share.
                split = state.expenseSplits[expense.id],
            )
        }
    }
}


/** "+ Add expense" at the bottom left of the Budget tab (the assistant button stays at the right). */
@Composable
internal fun AddExpenseFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
        text = { Text(stringResource(R.string.budget_add_expense), maxLines = 1) },
    )
}
