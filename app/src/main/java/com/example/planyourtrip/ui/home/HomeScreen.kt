package com.example.planyourtrip.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.components.BrandMark
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.home.components.NextTripCard
import com.example.planyourtrip.ui.home.components.PastTripCard
import com.example.planyourtrip.ui.home.components.UpcomingTripCard
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import java.time.LocalDate

@Composable
fun HomeScreen(
    onAddTrip: () -> Unit,
    onTripClick: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onAddTrip = onAddTrip,
        onTripClick = onTripClick,
        onSignOut = viewModel::onSignOut,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    onAddTrip: () -> Unit,
    onTripClick: (Long) -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTrip,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.home_add_trip), style = MaterialTheme.typography.titleMedium) },
            )
        },
    ) { innerPadding ->
        val content = state as? HomeUiState.Content ?: return@Scaffold
        val sections = content.sections
        val layoutDirection = LocalLayoutDirection.current

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Extra bottom space so the FAB (56dp + 16dp margin) never covers the last card.
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + Dimens.ScreenPadding,
                end = innerPadding.calculateEndPadding(layoutDirection) + Dimens.ScreenPadding,
                top = innerPadding.calculateTopPadding() + 32.dp,
                bottom = innerPadding.calculateBottomPadding() + 104.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header") {
                HomeHeader(content, onSignOut)
            }

            if (sections.isEmpty) {
                item(key = "empty") { EmptyState(onAddTrip) }
                return@LazyColumn
            }

            sections.next?.let { next ->
                item(key = "next") {
                    NextTripCard(
                        next = next,
                        onClick = { onTripClick(next.trip.id) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            if (sections.upcoming.isNotEmpty()) {
                item(key = "upcoming-header") {
                    SectionHeader(
                        title = stringResource(R.string.home_upcoming),
                        titleColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Pill(
                            text = pluralStringResource(
                                R.plurals.home_trip_count, sections.upcoming.size, sections.upcoming.size
                            ),
                            container = MaterialTheme.colorScheme.secondaryContainer,
                            content = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                items(sections.upcoming, key = { "upcoming-${it.trip.id}" }) { item ->
                    UpcomingTripCard(item = item, onClick = { onTripClick(item.trip.id) })
                }
            }

            if (sections.past.isNotEmpty()) {
                item(key = "past-header") {
                    SectionHeader(
                        title = stringResource(R.string.home_past_trips),
                        titleColor = ExtendedTheme.colors.onPastCard,
                    ) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.home_archived_count, sections.past.size, sections.past.size
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(sections.past, key = { "past-${it.id}" }) { trip ->
                    PastTripCard(trip = trip, onClick = { onTripClick(trip.id) })
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(content: HomeUiState.Content, onSignOut: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_greeting, content.firstName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(16.dp))
        AccountAvatar(content, onSignOut)
    }
}

/** Initials avatar that opens a menu with the user's name, email and "Sign out". */
@Composable
private fun AccountAvatar(content: HomeUiState.Content, onSignOut: () -> Unit) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val profileDescription = stringResource(R.string.home_profile, content.userName)
    Box {
        Surface(
            onClick = { menuOpen = true },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(Dimens.AvatarSize)
                .semantics { contentDescription = profileDescription },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = content.initials, style = MaterialTheme.typography.titleMedium)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = content.userName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (content.email.isNotEmpty()) {
                    Text(
                        text = content.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_sign_out)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_logout), contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onSignOut()
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    titleColor: Color,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
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

@Composable
private fun EmptyState(onAddTrip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 12.dp, end = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(size = 96.dp, iconSize = 44.dp)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PrimaryButton(text = stringResource(R.string.home_empty_button), onClick = onAddTrip)
    }
}

// ---------- Previews (sample data lives only here, never in the database) ----------

private val previewToday = LocalDate.of(2026, 10, 5)

private val previewTrips = listOf(
    Trip(1, "Rome, Italy", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY),
    Trip(2, "Athens, Greece", LocalDate.of(2027, 2, 3), LocalDate.of(2027, 2, 7), TripColor.SKY),
    Trip(3, "Lisbon, Portugal", LocalDate.of(2027, 4, 20), LocalDate.of(2027, 4, 26), TripColor.PEACH),
    Trip(4, "Prague, Czech Republic", LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 14), TripColor.MINT),
)

private fun previewState(trips: List<Trip>) = HomeUiState.Content(
    userName = "Etay Zahavi",
    email = "etay@example.com",
    firstName = "Etay",
    initials = "EZ",
    sections = groupTrips(trips, previewToday),
)

@Preview(name = "Home", widthDp = 411, heightDp = 891)
@Composable
private fun HomePreview() {
    PlanYourTripTheme(darkTheme = false) {
        HomeContent(previewState(previewTrips), onAddTrip = {}, onTripClick = {}, onSignOut = {})
    }
}

@Preview(name = "Home – narrow, large font", widthDp = 320, heightDp = 800, fontScale = 1.5f)
@Composable
private fun HomeNarrowPreview() {
    PlanYourTripTheme(darkTheme = false) {
        HomeContent(
            previewState(
                previewTrips + Trip(
                    5, "Reykjavík, Iceland", LocalDate.of(2027, 12, 28), LocalDate.of(2028, 1, 3), TripColor.LAVENDER
                )
            ),
            onAddTrip = {}, onTripClick = {}, onSignOut = {},
        )
    }
}

@Preview(name = "Home – dark", widthDp = 411, heightDp = 891)
@Composable
private fun HomeDarkPreview() {
    PlanYourTripTheme(darkTheme = true) {
        HomeContent(previewState(previewTrips), onAddTrip = {}, onTripClick = {}, onSignOut = {})
    }
}

@Preview(name = "Home – empty", widthDp = 411, heightDp = 891)
@Composable
private fun HomeEmptyPreview() {
    PlanYourTripTheme(darkTheme = false) {
        HomeContent(previewState(emptyList()), onAddTrip = {}, onTripClick = {}, onSignOut = {})
    }
}
