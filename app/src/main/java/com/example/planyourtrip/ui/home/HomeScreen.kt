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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.ui.common.account.AccountState
import com.example.planyourtrip.ui.common.account.AccountViewModel
import com.example.planyourtrip.ui.common.account.actions
import com.example.planyourtrip.ui.common.components.AccountActions
import com.example.planyourtrip.ui.common.components.AccountButton
import com.example.planyourtrip.ui.common.components.BrandMark
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.components.RefreshBox
import com.example.planyourtrip.ui.common.delete.DeleteTripHost
import com.example.planyourtrip.ui.common.delete.DeleteTripViewModel
import com.example.planyourtrip.ui.common.refresh.RefreshViewModel
import com.example.planyourtrip.ui.common.share.ShareTripHost
import com.example.planyourtrip.ui.common.share.ShareTripViewModel
import com.example.planyourtrip.ui.home.join.JoinTripHost
import com.example.planyourtrip.ui.home.join.JoinTripViewModel
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.Spacing

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
    HomeMessages(
        snackbarHostState = snackbarHostState,
        deleteMessage = deleteState.message,
        currentDeleteMessage = { deleteState.message },
        onDeleteMessageShown = deleteViewModel::onMessageShown,
        joinedDestination = joinState.joinedDestination,
        currentJoinedDestination = { joinState.joinedDestination },
        onJoinedShown = joinViewModel::onJoinedShown,
        refreshFailed = refresh.failed,
        onRefreshMessageShown = refreshViewModel::onMessageShown,
    )

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
        floatingActionButton = { HomeButtons(onJoinTrip, onAddTrip) },
    ) { innerPadding ->
        val content = state as? HomeUiState.Content ?: return@Scaffold
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
                    top = innerPadding.calculateTopPadding() + Spacing.xxxl,
                    bottom = innerPadding.calculateBottomPadding() + FabListClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                item(key = "header") {
                    HomeHeader(content, account, accountActions)
                }

                if (content.sections.isEmpty) {
                    item(key = "empty") { EmptyState(onAddTrip, Modifier.animateItem()) }
                    return@LazyColumn
                }

                tripSections(content, deletingTripId, TripCardActions(onTripClick, onShareTrip, onDeleteTrip))
            }
        }
    }
}

/** "Join trip" next to "Add trip", across the bottom (design/home_join.png). */
@Composable
private fun HomeButtons(onJoinTrip: () -> Unit, onAddTrip: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
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
            contentPadding = PaddingValues(horizontal = Spacing.l),
            modifier = Modifier
                .weight(1f)
                .height(JoinButtonHeight),
        ) {
            Icon(painterResource(R.drawable.ic_key), contentDescription = null)
            Spacer(Modifier.width(Spacing.s))
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
            Spacer(Modifier.width(Spacing.l))
            AccountButton(account, accountActions, size = Dimens.AvatarSize)
        }
    }
}

@Composable
private fun EmptyState(onAddTrip: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = EmptyStateTopPadding, start = Spacing.m, end = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(size = Dimens.EmptyStateMarkSize, iconSize = Dimens.EmptyStateMarkIconSize)
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxl))
        PrimaryButton(text = stringResource(R.string.home_empty_button), onClick = onAddTrip)
    }
}

/** The outlined "Join trip" button next to the floating "New trip" button. */
private val JoinButtonHeight = 56.dp

/** Extra space under the trip list, so the last card scrolls above the floating buttons. */
private val FabListClearance = 104.dp

/** The empty state's distance from the top bar. */
private val EmptyStateTopPadding = 64.dp
