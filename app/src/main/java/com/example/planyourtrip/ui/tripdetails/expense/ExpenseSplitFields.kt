package com.example.planyourtrip.ui.tripdetails.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.tripdetails.components.TravelerAvatar
import com.example.planyourtrip.ui.tripdetails.components.personName

/**
 * "Paid by" (one person) and "Split between" (one or more, plus the quick "Not shared" option). Shown only when the
 * trip has other travelers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SplitFields(
    state: ExpenseFormUiState,
    onPaidByChange: (String) -> Unit,
    onSplitToggle: (String) -> Unit,
    onNotShared: () -> Unit,
) {
    Text(
        text = stringResource(R.string.expense_paid_by),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
        state.people.forEach { person ->
            PersonChip(person, selected = person.key == state.paidBy, onClick = { onPaidByChange(person.key) })
        }
    }
    Text(
        text = stringResource(R.string.expense_split_between),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = Spacing.s),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = state.isNotShared,
            onClick = onNotShared,
            label = { Text(stringResource(R.string.expense_not_shared)) },
        )
        state.people.forEach { person ->
            PersonChip(
                person,
                selected = person.key in state.splitBetween,
                onClick = { onSplitToggle(person.key) },
            )
        }
    }
    if (state.splitError != null) {
        Text(
            text = stringResource(R.string.error_split_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = Spacing.l),
        )
    }
}

/** A chip with the person's small avatar and name. */
@Composable
private fun PersonChip(person: Person, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(personName(person), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { TravelerAvatar(person, Dimens.TravelerAvatarChipSize) },
    )
}
