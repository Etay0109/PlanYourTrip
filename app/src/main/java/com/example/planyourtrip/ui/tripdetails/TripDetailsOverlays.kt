package com.example.planyourtrip.ui.tripdetails

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.ui.common.components.CurrencyPickerSheet
import com.example.planyourtrip.ui.common.delete.DeleteTripHost
import com.example.planyourtrip.ui.common.delete.DeleteTripViewModel
import com.example.planyourtrip.ui.common.share.ShareTripHost
import com.example.planyourtrip.ui.common.share.ShareTripViewModel
import com.example.planyourtrip.ui.common.shortDestination
import com.example.planyourtrip.ui.tripdetails.members.InviteHost
import com.example.planyourtrip.ui.tripdetails.members.InviteViewModel
import com.example.planyourtrip.ui.tripdetails.prep.PrepDialogs
import com.example.planyourtrip.ui.tripdetails.prep.PrepViewModel
import com.example.planyourtrip.ui.tripdetails.split.SplitDialogs
import com.example.planyourtrip.ui.tripdetails.split.SplitViewModel

/** The sheets and dialogs over the trip screen: currency picker, Share, Delete/Leave, members, Prep and Split. */
@Composable
internal fun TripDetailsOverlays(
    state: TripDetailsUiState,
    viewModel: TripDetailsViewModel,
    shareViewModel: ShareTripViewModel,
    deleteViewModel: DeleteTripViewModel,
    inviteViewModel: InviteViewModel,
    prepViewModel: PrepViewModel,
    splitViewModel: SplitViewModel,
    snackbarHostState: SnackbarHostState,
    onTripDeleted: (Long) -> Unit,
    onTripLeft: (Long) -> Unit,
) {
    val currencyPicker by viewModel.currencyPicker.collectAsStateWithLifecycle()
    val deleteState by deleteViewModel.uiState.collectAsStateWithLifecycle()
    currencyPicker?.let { picker ->
        CurrencyPickerSheet(
            state = picker,
            onQueryChange = viewModel::onCurrencyQueryChange,
            onSelect = viewModel::onCurrencySelected,
            onRetry = viewModel::onRetryCurrencies,
            onDismiss = viewModel::onDismissCurrencyPicker,
        )
    }
    ShareTripHost(shareViewModel)
    // Confirmed here, deleted (or left) on Home (the trip screen would otherwise show "not found" for a moment).
    DeleteTripHost(
        deleteViewModel,
        onConfirm = {
            val leave = deleteState.dialog?.leave == true
            deleteViewModel.takeConfirmed()?.let { id -> if (leave) onTripLeft(id) else onTripDeleted(id) }
        },
    )
    val loadedTrip = (state as? TripDetailsUiState.Loaded)?.trip
    InviteHost(
        viewModel = inviteViewModel,
        members = (state as? TripDetailsUiState.Loaded)?.members,
        // "Invite to London" and the shared message use the short name ("London, United Kingdom" → "London").
        destination = loadedTrip?.let { shortDestination(it.destination, it.countryCode) }.orEmpty(),
        snackbarHostState = snackbarHostState,
        onLeave = { loadedTrip?.let(deleteViewModel::onRemoveTrip) },
    )
    PrepDialogs(prepViewModel)
    SplitDialogs(splitViewModel)
}
