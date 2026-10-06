package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseCurrency
import com.example.planyourtrip.ui.tripdetails.labelRes

/**
 * One expense: round category icon, name, date and the category label; on the right the shekel amount in bold
 * and, for an expense entered in another currency, its original amount ("€10.00") in small gray text.
 * Tapping opens it for editing.
 */
@Composable
fun ExpenseRow(expense: Expense, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val categoryColor = ExtendedTheme.colors.category(expense.category)
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryIcon(expense.category)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = expense.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = TripFormatting.date(expense.date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                )
                Spacer(Modifier.height(4.dp))
                TagPill(
                    text = stringResource(expense.category.labelRes()),
                    container = categoryColor.container,
                    content = categoryColor.content,
                )
            }
            Spacer(Modifier.width(12.dp))
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
            }
        }
    }
}
