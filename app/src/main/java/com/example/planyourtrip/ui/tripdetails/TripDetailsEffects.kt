package com.example.planyourtrip.ui.tripdetails

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.refresh.RefreshViewModel
import kotlinx.coroutines.launch

/** "Activity deleted" with "Undo" after an activity is deleted, and "Couldn't refresh" after a failed pull to refresh. */
@Composable
internal fun TripDetailsSnackbars(
    viewModel: TripDetailsViewModel,
    refreshViewModel: RefreshViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val deletedActivity by viewModel.deletedActivity.collectAsStateWithLifecycle()
    val refresh by refreshViewModel.state.collectAsStateWithLifecycle()
    // "Activity deleted" with "Undo", once per delete; Undo brings the activity back.
    val deletedMessage = stringResource(R.string.activity_deleted)
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(deletedActivity) {
        val deleted = deletedActivity ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.onUndoDelete(deleted)
        } else {
            viewModel.onDeleteMessageDismissed(deleted)
        }
    }
    val refreshFailedText = stringResource(R.string.refresh_failed)
    LaunchedEffect(refresh.failed) {
        if (!refresh.failed) return@LaunchedEffect
        // Cleared only after the snackbar: clearing first changes this effect's key, which cancels the snackbar.
        try {
            snackbarHostState.showSnackbar(refreshFailedText)
        } finally {
            refreshViewModel.onMessageShown()
        }
    }
}

/**
 * Opens a Google Maps URL: the map card (destination) and the Plan cards (activity location) use it. The app, or the
 * browser; nothing at all → a message.
 */
@Composable
internal fun rememberOpenMapsUrl(snackbarHostState: SnackbarHostState): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val noMapsApp = stringResource(R.string.trip_no_maps_app)
    val openUrl: (String) -> Unit = remember(context, snackbarHostState, noMapsApp) {
        { url -> if (!openGoogleMaps(context, url)) scope.launch { snackbarHostState.showSnackbar(noMapsApp) } }
    }
    return openUrl
}

/** Opens a Google Maps [url]; false when no app (not even a browser) can open it. */
private fun openGoogleMaps(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    true
} catch (e: ActivityNotFoundException) {
    false
}
