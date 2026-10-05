package com.example.planyourtrip.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.planyourtrip.ui.auth.AuthScreen
import com.example.planyourtrip.ui.home.HomeScreen
import com.example.planyourtrip.ui.tripdetails.TripDetailsScreen
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormScreen
import com.example.planyourtrip.ui.tripform.TripFormScreen

/**
 * Start: Home when a session is saved, otherwise Login.
 * Login → Home (Login removed from the back stack, so Back on Home exits the app)
 * Home → TripForm() (new trip; pops back to Home after saving)
 * Home → TripDetails(tripId) → TripForm(tripId) (edit trip) / ExpenseForm(tripId, expenseId?) — each pops back
 * Signed out (from the avatar menu or by the backend) → Login, with the whole back stack cleared.
 */
@Composable
fun AppNavHost(session: SessionViewModel = viewModel(factory = SessionViewModel.Factory)) {
    val navController = rememberNavController()
    val startDestination: Any = remember { if (session.startsSignedIn) Home else Login }

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
        composable<Home> {
            HomeScreen(
                onAddTrip = { navController.navigate(TripForm()) { launchSingleTop = true } },
                onTripClick = { id -> navController.navigate(TripDetails(id)) { launchSingleTop = true } },
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
            )
        }
        composable<ExpenseForm> {
            ExpenseFormScreen(
                onBack = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }
    }
}
