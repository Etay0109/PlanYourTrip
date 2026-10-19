package com.example.planyourtrip.ui.home

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.delete.DeleteTripMessage

/**
 * Home's snackbars: a trip deleted or left (or failing to), a trip joined, and a failed pull to refresh. Each message
 * is cleared after its snackbar (clearing first changes the effect's key and cancels it), and only if it is still the
 * current one ([currentDeleteMessage], [currentJoinedDestination]): a newer message replaces it without being cleared.
 */
@Composable
fun HomeMessages(
    snackbarHostState: SnackbarHostState,
    deleteMessage: DeleteTripMessage?,
    currentDeleteMessage: () -> DeleteTripMessage?,
    onDeleteMessageShown: () -> Unit,
    joinedDestination: String?,
    currentJoinedDestination: () -> String?,
    onJoinedShown: () -> Unit,
    refreshFailed: Boolean,
    onRefreshMessageShown: () -> Unit,
) {
    val deletedText = stringResource(R.string.trip_deleted)
    val failedText = stringResource(R.string.trip_delete_failed)
    val leftText = stringResource(R.string.trip_left)
    val leaveFailedText = stringResource(R.string.trip_leave_failed)
    LaunchedEffect(deleteMessage) {
        val message = deleteMessage ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(
                when (message) {
                    DeleteTripMessage.DELETED -> deletedText
                    DeleteTripMessage.FAILED -> failedText
                    DeleteTripMessage.LEFT -> leftText
                    DeleteTripMessage.LEAVE_FAILED -> leaveFailedText
                },
            )
        } finally {
            if (currentDeleteMessage() == message) onDeleteMessageShown()
        }
    }
    val joinedText = joinedDestination?.let { stringResource(R.string.join_joined, it) }
    LaunchedEffect(joinedDestination) {
        val text = joinedText ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(text)
        } finally {
            if (currentJoinedDestination() == joinedDestination) onJoinedShown()
        }
    }
    val refreshFailedText = stringResource(R.string.refresh_failed)
    LaunchedEffect(refreshFailed) {
        if (!refreshFailed) return@LaunchedEffect
        // Cleared only after the snackbar: clearing first changes this effect's key, which cancels the snackbar.
        try {
            snackbarHostState.showSnackbar(refreshFailedText)
        } finally {
            onRefreshMessageShown()
        }
    }
}
