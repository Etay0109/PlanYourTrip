package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseCurrency

/** Who paid an expense and who shares it, shown only when the trip has other travelers. */
@Immutable
data class ExpenseSplitInfo(
    val payer: Person,
    /** In list order; never empty. */
    val splitWith: List<Person>,
    val myShareAgorot: Long,
)

/**
 * One expense: round category icon, name, the date with the category label and, with other travelers ([split]),
 * a gray line with the payer's small avatar ("Paid by Noa · split 3 ways"). On the right the shekel amount in bold,
 * the original amount ("€10.00") for a foreign expense, and "Your share ₪60" in teal. Tapping opens it for editing.
 */
@Composable
fun ExpenseRow(
    expense: Expense,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    split: ExpenseSplitInfo? = null,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = Dimens.CardElevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIcon(expense.category)
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) { ExpenseDetails(expense, split) }
            Spacer(Modifier.width(Spacing.m))
            ExpenseAmounts(expense, split)
        }
    }
}

/** The name, the date with the category label and, with other travelers, the payer's line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseDetails(expense: Expense, split: ExpenseSplitInfo?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val categoryColor = ExtendedTheme.colors.category(expense.category)
    Text(
        text = expense.name,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(Spacing.xs))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = TripFormatting.date(expense.date),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
        )
        TagPill(
            text = stringResource(expense.category.labelRes()),
            container = categoryColor.container,
            content = categoryColor.content,
        )
    }
    if (split != null) {
        Spacer(Modifier.height(Spacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TravelerAvatar(split.payer, Dimens.TravelerAvatarTinySize)
            Spacer(Modifier.width(Spacing.xsPlus))
            Text(
                text = splitLine(split),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }
}

/** The shekel amount, the original foreign amount and "Your share". */
@Composable
private fun ExpenseAmounts(expense: Expense, split: ExpenseSplitInfo?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = shekels(expense.amountAgorot),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        expense.original?.let { original ->
            Text(
                text = ExpenseCurrency.formatOriginal(original),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
        if (split != null) {
            Text(
                text = stringResource(R.string.expense_row_your_share, shekels(split.myShareAgorot)),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** "Paid by Noa · split 3 ways", "Paid by you · not shared", "Paid by you · for Noa". */
@Composable
private fun splitLine(split: ExpenseSplitInfo): String {
    val paidBy = if (split.payer is Person.You) {
        stringResource(R.string.expense_row_paid_by_you)
    } else {
        stringResource(R.string.expense_row_paid_by, personName(split.payer))
    }
    val single = split.splitWith.singleOrNull()
    return when {
        single == split.payer -> stringResource(R.string.expense_row_not_shared, paidBy)
        single != null -> stringResource(R.string.expense_row_for, paidBy, personForSentence(single))
        else -> pluralStringResource(R.plurals.expense_row_split, split.splitWith.size, paidBy, split.splitWith.size)
    }
}

/** "you" in the middle of a sentence, otherwise the name. */
@Composable
private fun personForSentence(person: Person): String =
    if (person is Person.You) stringResource(R.string.expense_row_you_lowercase) else personName(person)
