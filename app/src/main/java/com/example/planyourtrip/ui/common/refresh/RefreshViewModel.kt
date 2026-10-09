package com.example.planyourtrip.ui.common.refresh

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.sync.SyncController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Pull to refresh: the spinner, and a message when the refresh didn't reach the cloud. */
data class RefreshState(
    val isRefreshing: Boolean = false,
    /** "Couldn't refresh. Check your connection."; cleared with [RefreshViewModel.onMessageShown]. */
    val failed: Boolean = false,
)

/**
 * Pull to refresh on Home and the trip screen: a full sync now ([SyncController.refresh]). Room stays the only
 * source of the screens, so whatever the sync brings shows up through their usual flows. Each screen has its own.
 */
class RefreshViewModel(private val syncController: SyncController) : ViewModel() {
    private val _state = MutableStateFlow(RefreshState())
    val state: StateFlow<RefreshState> = _state.asStateFlow()

    /** Set synchronously, so pulling again while a refresh runs starts nothing. */
    fun onRefresh() {
        if (_state.value.isRefreshing) return
        _state.value = RefreshState(isRefreshing = true)
        viewModelScope.launch {
            val ok = syncController.refresh()
            _state.value = RefreshState(isRefreshing = false, failed = !ok)
        }
    }

    fun onMessageShown() = _state.update { it.copy(failed = false) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { RefreshViewModel(appContainer().syncController) }
        }
    }
}
