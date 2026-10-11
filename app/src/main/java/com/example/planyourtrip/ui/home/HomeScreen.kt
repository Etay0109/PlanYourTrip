package com.example.planyourtrip.ui.home

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.account.AccountState
import com.example.planyourtrip.ui.common.account.AccountViewModel
import com.example.planyourtrip.ui.common.account.actions
import com.example.planyourtrip.ui.common.components.AccountActions
import com.example.planyourtrip.ui.common.components.AccountButton
import com.example.planyourtrip.ui.common.components.BrandMark
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.components.RefreshBox
import com.example.planyourtrip.ui.common.delete.DeleteTripHost
import com.example.planyourtrip.ui.common.delete.DeleteTripMessage
import com.example.planyourtrip.ui.common.delete.DeleteTripViewModel
import com.example.planyourtrip.ui.common.refresh.RefreshViewModel
import com.example.planyourtrip.ui.common.share.ShareTripHost
import com.example.planyourtrip.ui.common.share.ShareTripViewModel
import com.example.planyourtrip.ui.home.components.NextTripCard
import com.example.planyourtrip.ui.home.components.PastTripCard
import com.example.planyourtrip.ui.home.components.SwipeToDeleteBox
import com.example.planyourtrip.ui.home.components.UpcomingTripCard
import com.example.planyourtrip.ui.home.join.JoinTripHost
import com.example.planyourtrip.ui.home.join.JoinTripViewModel
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import java.time.LocalDate

@Composable
fun HomeScreen(
    onAddTrip: () -> Unit,
    onTripClick: (Long) -> Unit,
    /** A trip whose delete was confirmed on the trip screen; deleted here, then [onPendingDeleteHandled]. */
    pendingDeleteTripId: Long? = null,
    onPendingDeleteHandled: () -> Unit = {},
    /** A shared trip whose leave was confirmed on the trip screen; left here, then [onPendingLeaveHandled]. */
    pendingLeaveTripId: Long? = null,
    onPendingLeaveHandled: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    shareViewModel: ShareTripViewModel = viewModel(factory = ShareTripViewModel.Factory),
    accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory),
    deleteViewModel: DeleteTripViewModel = viewModel(factory = DeleteTripViewModel.Factory),
    refreshViewModel: RefreshViewModel = viewModel(factory = RefreshViewModel.Factory),
    joinViewModel: JoinTripViewModel = viewModel(factory = JoinTripViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val joinState by joinViewModel.uiState.collectAsStateWithLifecycle()
    val refresh by refreshViewModel.state.collectAsStateWithLifecycle()
    val account by accountViewModel.state.collectAsStateWithLifecycle()
    val deleteState by deleteViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pendingDeleteTripId) {
        val id = pendingDeleteTripId ?: return@LaunchedEffect
        onPendingDeleteHandled()
        deleteViewModel.deleteConfirmed(id)
    }
    LaunchedEffect(pendingLeaveTripId) {
        val id = pendingLeaveTripId ?: return@LaunchedEffect
        onPendingLeaveHandled()
        deleteViewModel.leaveConfirmed(id)
    }
    val deletedText = stringResource(R.string.trip_deleted)
    val failedText = stringResource(R.string.trip_delete_failed)
    val leftText = stringResource(R.string.trip_left)
    val leaveFailedText = stringResource(R.string.trip_leave_failed)
    LaunchedEffect(deleteState.message) {
        val message = deleteState.message ?: return@LaunchedEffect
        deleteViewModel.onMessageShown()
        snackbarHostState.showSnackbar(
            when (message) {
                DeleteTripMessage.DELETED -> deletedText
                DeleteTripMessage.FAILED -> failedText
                DeleteTripMessage.LEFT -> leftText
                DeleteTripMessage.LEAVE_FAILED -> leaveFailedText
            },
        )
    }
    val joinedDestination = joinState.joinedDestination
    val joinedText = joinedDestination?.let { stringResource(R.string.join_joined, it) }
    LaunchedEffect(joinedDestination) {
        val text = joinedText ?: return@LaunchedEffect
        joinViewModel.onJoinedShown()
        snackbarHostState.showSnackbar(text)
    }
    val refreshFailedText = stringResource(R.string.refresh_failed)
    LaunchedEffect(refresh.failed) {
        if (!refresh.failed) return@LaunchedEffect
        refreshViewModel.onMessageShown()
        snackbarHostState.showSnackbar(refreshFailedText)
    }

    ShareTripHost(shareViewModel)
    DeleteTripHost(deleteViewModel)
    JoinTripHost(joinViewModel, joinState)
    HomeContent(
        state = state,
        account = account,
        onAddTrip = onAddTrip,
        onJoinTrip = joinViewModel::onOpen,
        onTripClick = onTripClick,
        onShareTrip = shareViewModel::onShareTrip,
        onDeleteTrip = deleteViewModel::onRemoveTrip,
        deletingTripId = deleteState.dialog?.tripId,
        snackbarHostState = snackbarHostState,
        accountActions = remember(accountViewModel) { accountViewModel.actions() },
        isRefreshing = refresh.isRefreshing,
        onRefresh = refreshViewModel::onRefresh,
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    /** The profile menu (null = no avatar). */
    account: AccountState?,
    onAddTrip: () -> Unit,
    onTripClick: (Long) -> Unit,
    onShareTrip: (Long) -> Unit,
    /** Opens the "Join a trip" sheet. */
    onJoinTrip: () -> Unit = {},
    /** Opens the "Delete this trip?" (or, for a shared trip someone else owns, "Leave this trip?") dialog. */
    onDeleteTrip: (Trip) -> Unit = {},
    /** The trip whose delete dialog is open: its card stays swiped open until the dialog closes. */
    deletingTripId: Long? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    accountActions: AccountActions = AccountActions({}, {}, {}),
    /** Pull to refresh: a full sync with the cloud. */
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            // "Join trip" next to "Add trip", across the bottom (design/home_join.png).
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenPadding),
            ) {
                OutlinedButton(
                    onClick = onJoinTrip,
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_key), contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.join_trip),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = onAddTrip,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                    text = {
                        Text(
                            stringResource(R.string.home_add_trip),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { innerPadding ->
        val content = state as? HomeUiState.Content ?: return@Scaffold
        val sections = content.sections
        val layoutDirection = LocalLayoutDirection.current

        RefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
            indicatorTop = innerPadding.calculateTopPadding(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Extra bottom space so the buttons (56dp + 16dp margin) never cover the last card.
                contentPadding = PaddingValues(
                    start = innerPadding.calculateStartPadding(layoutDirection) + Dimens.ScreenPadding,
                    end = innerPadding.calculateEndPadding(layoutDirection) + Dimens.ScreenPadding,
                    top = innerPadding.calculateTopPadding() + 32.dp,
                    bottom = innerPadding.calculateBottomPadding() + 104.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "header") {
                    HomeHeader(content, account, accountActions)
                }

                if (sections.isEmpty) {
                    item(key = "empty") { EmptyState(onAddTrip, Modifier.animateItem()) }
                    return@LazyColumn
                }

                // Every card is keyed by its trip, whatever its section: a deleted card fades out and the trip that
                // takes the Next trip place moves there from the Upcoming list.
                sections.next?.let { next ->
                    item(key = tripKey(next.trip.id)) {
                        SwipeToDeleteBox(
                            shape = MaterialTheme.shapes.extraLarge,
                            deletePending = deletingTripId == next.trip.id,
                            onDelete = { onDeleteTrip(next.trip) },
                            leave = !content.isOwner(next.trip),
                            modifier = Modifier.animateItem().padding(top = 8.dp),
                        ) {
                            NextTripCard(
                                next = next,
                                onClick = { onTripClick(next.trip.id) },
                                onShare = { onShareTrip(next.trip.id) },
                                onDelete = { onDeleteTrip(next.trip) },
                                isOwner = content.isOwner(next.trip),
                                photoUrl = content.photos[next.trip.id]?.cardUrl,
                            )
                        }
                    }
                }

                if (sections.upcoming.isNotEmpty()) {
                    item(key = "upcoming-header") {
                        SectionHeader(
                            modifier = Modifier.animateItem(),
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
                    items(sections.upcoming, key = { tripKey(it.trip.id) }) { item ->
                        SwipeToDeleteBox(
                            shape = MaterialTheme.shapes.large,
                            deletePending = deletingTripId == item.trip.id,
                            onDelete = { onDeleteTrip(item.trip) },
                            leave = !content.isOwner(item.trip),
                            modifier = Modifier.animateItem(),
                        ) {
                            UpcomingTripCard(
                                item = item,
                                onClick = { onTripClick(item.trip.id) },
                                onShare = { onShareTrip(item.trip.id) },
                                onDelete = { onDeleteTrip(item.trip) },
                                isOwner = content.isOwner(item.trip),
                                thumbUrl = content.photos[item.trip.id]?.thumbUrl,
                            )
                        }
                    }
                }

                if (sections.past.isNotEmpty()) {
                    item(key = "past-header") {
                        SectionHeader(
                            modifier = Modifier.animateItem(),
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
                    items(sections.past, key = { tripKey(it.id) }) { trip ->
                        SwipeToDeleteBox(
                            shape = MaterialTheme.shapes.large,
                            deletePending = deletingTripId == trip.id,
                            onDelete = { onDeleteTrip(trip) },
                            leave = !content.isOwner(trip),
                            modifier = Modifier.animateItem(),
                        ) {
                            PastTripCard(
                                trip = trip,
                                onClick = { onTripClick(trip.id) },
                                onShare = { onShareTrip(trip.id) },
                                onDelete = { onDeleteTrip(trip) },
                                isOwner = content.isOwner(trip),
                                thumbUrl = content.photos[trip.id]?.thumbUrl,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(content: HomeUiState.Content, account: AccountState?, accountActions: AccountActions) {
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
        if (account != null) {
            Spacer(Modifier.width(16.dp))
            AccountButton(account, accountActions, size = Dimens.AvatarSize)
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
private fun EmptyState(onAddTrip: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
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
    firstName = "Etay",
    sections = groupTrips(trips, previewToday),
)

private val previewAccount = AccountState(userName = "Etay Zahavi", email = "etay@example.com", initials = "EZ")

@Preview(name = "Home", widthDp = 411, heightDp = 891)
@Composable
private fun HomePreview() {
    PlanYourTripTheme(darkTheme = false) {
        HomeContent(previewState(previewTrips), previewAccount, onAddTrip = {}, onTripClick = {}, onShareTrip = {})
    }
}

@Preview(name = "Home – narrow, large font", widthDp = 320, heightDp = 800, fontScale = 1.5f)
@Composable
private fun HomeNarrowPreview() {
    PlanYourTripTheme(darkTheme = false) {
        HomeContent(
            previewState(
                previewTrips + listOf(
                    Trip(
                        5, "Reykjavík, Iceland", LocalDate.of(2027, 12, 28), LocalDate.of(2028, 1, 3),
                        TripColor.LAVENDER,
                    ),
                    // Long name and long dates next to the three-dots button: nothing may be cut off.
                    Trip(
                        6, "San Carlos de Bariloche, Argentina", LocalDate.of(2026, 3, 28),
                        LocalDate.of(2026, 4, 3), TripColor.ROSE,
                    ),
                )
            ),
            previewAccount,
            onAddTrip = {}, onTripClick = {}, onShareTrip = {},
        )
    }
}

@Preview(name = "Home – dark", widthDp = 411, heightDp = 891)
@Composable
private fun HomeDarkPreview() {
    PlanYourTripTheme(darkTheme = true) {
        HomeContent(previewState(previewTrips), previewAccount, onAddTrip = {}, onTripClick = {}, onShareTrip = {})
    }
}

@Preview(name = "Home – empty", widthDp = 411, heightDp = 891)
@Composable
private fun HomeEmptyPreview() {
    PlanYourTripTheme(darkTheme = false) {
        HomeContent(previewState(emptyList()), previewAccount, onAddTrip = {}, onTripClick = {}, onShareTrip = {})
    }
}
