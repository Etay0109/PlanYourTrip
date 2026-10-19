package com.example.planyourtrip.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.home.components.NextTripCard
import com.example.planyourtrip.ui.home.components.PastTripCard
import com.example.planyourtrip.ui.home.components.SwipeToDeleteBox
import com.example.planyourtrip.ui.home.components.UpcomingTripCard
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.Spacing

/** What a trip card can do: open, share, and delete (or leave). */
class TripCardActions(
    val onTripClick: (Long) -> Unit,
    val onShareTrip: (Long) -> Unit,
    /** Opens the "Delete this trip?" (or, for a shared trip someone else owns, "Leave this trip?") dialog. */
    val onDeleteTrip: (Trip) -> Unit,
)

/**
 * Home's trip cards: the "Next trip" card, then the Upcoming and Past sections. Every card is keyed by its trip,
 * whatever its section: a deleted card fades out and the trip that takes the Next trip place moves there from the
 * Upcoming list. [deletingTripId]'s card stays swiped open until its dialog closes.
 */
fun LazyListScope.tripSections(content: HomeUiState.Content, deletingTripId: Long?, actions: TripCardActions) {
    val sections = content.sections
    sections.next?.let { next ->
        item(key = tripKey(next.trip.id)) {
            SwipeToDeleteBox(
                shape = MaterialTheme.shapes.extraLarge,
                deletePending = deletingTripId == next.trip.id,
                onDelete = { actions.onDeleteTrip(next.trip) },
                leave = !content.isOwner(next.trip),
                modifier = Modifier.animateItem().padding(top = Spacing.s),
            ) {
                NextTripCard(
                    next = next,
                    onClick = { actions.onTripClick(next.trip.id) },
                    onShare = { actions.onShareTrip(next.trip.id) },
                    onDelete = { actions.onDeleteTrip(next.trip) },
                    isOwner = content.isOwner(next.trip),
                    photoUrl = content.photos[next.trip.id]?.cardUrl,
                    currentYear = content.currentYear,
                )
            }
        }
    }
    if (sections.upcoming.isNotEmpty()) upcomingSection(content, deletingTripId, actions)
    if (sections.past.isNotEmpty()) pastSection(content, deletingTripId, actions)
}

private fun LazyListScope.upcomingSection(content: HomeUiState.Content, deletingTripId: Long?, actions: TripCardActions) {
    val upcoming = content.sections.upcoming
    item(key = "upcoming-header") {
        SectionHeader(
            modifier = Modifier.animateItem(),
            title = stringResource(R.string.home_upcoming),
            titleColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Pill(
                text = pluralStringResource(R.plurals.home_trip_count, upcoming.size, upcoming.size),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.primary,
            )
        }
    }
    items(upcoming, key = { tripKey(it.trip.id) }) { item ->
        SwipeToDeleteBox(
            shape = MaterialTheme.shapes.large,
            deletePending = deletingTripId == item.trip.id,
            onDelete = { actions.onDeleteTrip(item.trip) },
            leave = !content.isOwner(item.trip),
            modifier = Modifier.animateItem(),
        ) {
            UpcomingTripCard(
                item = item,
                onClick = { actions.onTripClick(item.trip.id) },
                onShare = { actions.onShareTrip(item.trip.id) },
                onDelete = { actions.onDeleteTrip(item.trip) },
                isOwner = content.isOwner(item.trip),
                thumbUrl = content.photos[item.trip.id]?.thumbUrl,
                currentYear = content.currentYear,
            )
        }
    }
}

private fun LazyListScope.pastSection(content: HomeUiState.Content, deletingTripId: Long?, actions: TripCardActions) {
    val past = content.sections.past
    item(key = "past-header") {
        SectionHeader(
            modifier = Modifier.animateItem(),
            title = stringResource(R.string.home_past_trips),
            titleColor = ExtendedTheme.colors.onPastCard,
        ) {
            Text(
                text = pluralStringResource(R.plurals.home_archived_count, past.size, past.size),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(past, key = { tripKey(it.id) }) { trip ->
        SwipeToDeleteBox(
            shape = MaterialTheme.shapes.large,
            deletePending = deletingTripId == trip.id,
            onDelete = { actions.onDeleteTrip(trip) },
            leave = !content.isOwner(trip),
            modifier = Modifier.animateItem(),
        ) {
            PastTripCard(
                trip = trip,
                onClick = { actions.onTripClick(trip.id) },
                onShare = { actions.onShareTrip(trip.id) },
                onDelete = { actions.onDeleteTrip(trip) },
                isOwner = content.isOwner(trip),
                thumbUrl = content.photos[trip.id]?.thumbUrl,
            )
        }
    }
}

private fun tripKey(id: Long) = "trip-$id"

@Composable
private fun SectionHeader(
    title: String,
    titleColor: Color,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = titleColor,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
