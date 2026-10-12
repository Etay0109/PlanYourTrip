package com.example.planyourtrip.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.StateFlow
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.planyourtrip.data.reminder.ReminderLink
import com.example.planyourtrip.ui.auth.AuthScreen
import com.example.planyourtrip.ui.common.NotificationPermissionHost
import com.example.planyourtrip.ui.home.HomeScreen
import com.example.planyourtrip.ui.tripdetails.TripDetailsScreen
import com.example.planyourtrip.ui.tripdetails.TripTab
import com.example.planyourtrip.ui.tripdetails.activity.ActivityFormScreen
import com.example.planyourtrip.ui.tripdetails.assistant.AssistantScreen
import com.example.planyourtrip.ui.tripdetails.assistant.AssistantViewModel
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormScreen
import com.example.planyourtrip.ui.tripform.TripFormScreen

/**
 * Start: Home when a session is saved, otherwise Login.
 * Login → Home (Login removed from the back stack, so Back on Home exits the app)
 * Home → TripForm() (new trip; pops back to Home after saving)
 * Home → TripDetails(tripId) → TripForm(tripId) (edit trip) / ExpenseForm(tripId, expenseId?) /
 *   ActivityForm(tripId, activityId?, dateEpochDay?) / TripAssistant(tripId) — each pops back
 * TripDetails → "Delete trip" confirmed → back to Home, which deletes it ([PENDING_DELETE_TRIP])
 * Signed out (from the avatar menu or by the backend) → Login, with the whole back stack cleared.
 * A tapped trip reminder ([reminderLink]) → Home → TripDetails(tripId, Prep), when the trip is still the signed-in
 *   account's; otherwise the app opens as usual.
 */
@Composable
fun AppNavHost(
    reminderLink: StateFlow<ReminderLink?>,
    onReminderLinkHandled: () -> Unit,
    session: SessionViewModel = viewModel(factory = SessionViewModel.Factory),
) {
    val navController = rememberNavController()
    val startDestination: Any = remember { if (session.startsSignedIn) Home else Login }

    // Above the NavHost: the trip form that triggers it has already popped when the question is shown.
    NotificationPermissionHost(requests = session.permissionRequests, onAsked = session::onPermissionAsked)

    LaunchedEffect(navController) {
        session.currentUser.collect { user ->
            val onLogin = navController.currentDestination?.hasRoute<Login>() ?: true
            if (user == null && !onLogin) {
                navController.navigate(Login) {
                    popUpTo(navController.graph.id) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable<Login> {
            AuthScreen(
                onAuthenticated = {
                    navController.navigate(Home) {
                        popUpTo<Login> { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<Home> { entry ->
            // A delete confirmed on the trip screen, handed over when it popped back here.
            val pendingDelete by entry.savedStateHandle.getStateFlow<Long?>(PENDING_DELETE_TRIP, null)
                .collectAsStateWithLifecycle()
            // The same for leaving a shared trip.
            val pendingLeave by entry.savedStateHandle.getStateFlow<Long?>(PENDING_LEAVE_TRIP, null)
                .collectAsStateWithLifecycle()
            HomeScreen(
                onAddTrip = { navController.navigate(TripForm()) { launchSingleTop = true } },
                onTripClick = { id -> navController.navigate(TripDetails(id)) { launchSingleTop = true } },
                pendingDeleteTripId = pendingDelete,
                onPendingDeleteHandled = { entry.savedStateHandle[PENDING_DELETE_TRIP] = null },
                pendingLeaveTripId = pendingLeave,
                onPendingLeaveHandled = { entry.savedStateHandle[PENDING_LEAVE_TRIP] = null },
            )
        }
        composable<TripForm> {
            TripFormScreen(
                onBack = { navController.popBackStack() },
                // A new trip returns to Home; an edited trip returns to its details screen.
                onSaved = { navController.popBackStack() },
            )
        }
        composable<TripDetails> { entry ->
            val tripId = entry.toRoute<TripDetails>().tripId
            TripDetailsScreen(
                onBack = { navController.popBackStack() },
                onEditTrip = { navController.navigate(TripForm(tripId)) { launchSingleTop = true } },
                onAddExpense = { navController.navigate(ExpenseForm(tripId)) { launchSingleTop = true } },
                onExpenseClick = { expenseId ->
                    navController.navigate(ExpenseForm(tripId, expenseId)) { launchSingleTop = true }
                },
                onAddActivity = { day ->
                    navController.navigate(ActivityForm(tripId, dateEpochDay = day.toEpochDay())) {
                        launchSingleTop = true
                    }
                },
                onActivityClick = { activityId ->
                    navController.navigate(ActivityForm(tripId, activityId)) { launchSingleTop = true }
                },
                onOpenAssistant = { navController.navigate(TripAssistant(tripId)) { launchSingleTop = true } },
                onTripDeleted = { id ->
                    // Back to Home first; Home deletes the trip, animates its card out and says "Trip deleted".
                    navController.getBackStackEntry<Home>().savedStateHandle[PENDING_DELETE_TRIP] = id
                    navController.popBackStack<Home>(inclusive = false)
                },
                initialTab = entry.toRoute<TripDetails>().initialTab
                    ?.let { name -> TripTab.entries.firstOrNull { it.name == name } }
                    ?: TripTab.OVERVIEW,
                onTripLeft = { id ->
                    // Back to Home first; Home leaves the trip, animates its card out and says "You left the trip".
                    navController.getBackStackEntry<Home>().savedStateHandle[PENDING_LEAVE_TRIP] = id
                    navController.popBackStack<Home>(inclusive = false)
                },
            )
        }
        composable<TripAssistant> { entry ->
            // Scoped to the trip screen's entry: the conversation survives closing and reopening the assistant,
            // and ends when the trip screen is popped.
            val tripEntry = remember(entry) { navController.getBackStackEntry<TripDetails>() }
            AssistantScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel(tripEntry, factory = AssistantViewModel.Factory),
            )
        }
        composable<ExpenseForm> {
            ExpenseFormScreen(
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
        composable<ActivityForm> {
            ActivityFormScreen(
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
    }

    val link by reminderLink.collectAsStateWithLifecycle()
    LaunchedEffect(link) {
        val tapped = link ?: return@LaunchedEffect
        val tripId = session.tripIdFor(tapped)
        // Home stays below the trip, so Back returns to it.
        if (tripId != null) navController.navigate(TripDetails(tripId, initialTab = TripTab.PREP.name)) { popUpTo<Home>() }
        // Last: clearing the link restarts this effect.
        onReminderLinkHandled()
    }
}

/** Home's saved-state key for a trip whose delete was confirmed on the trip screen. */
private const val PENDING_DELETE_TRIP = "pendingDeleteTripId"

/** Home's saved-state key for a shared trip whose leave was confirmed on the trip screen. */
private const val PENDING_LEAVE_TRIP = "pendingLeaveTripId"
