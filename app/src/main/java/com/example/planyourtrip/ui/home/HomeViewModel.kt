package com.example.planyourtrip.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.sync.SignOutResult
import com.example.planyourtrip.data.sync.SyncController
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.data.user.firstName
import com.example.planyourtrip.data.user.greetingName
import com.example.planyourtrip.data.user.initials
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    tripRepository: TripRepository,
    private val authRepository: AuthRepository,
    private val syncController: SyncController,
    private val clock: Clock,
    /** Where trips are grouped and sorted; tests pass their test dispatcher. */
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val signOutWarning = MutableStateFlow(false)

    /** The signed-in user's trips; switches automatically when the account changes. */
    val uiState: StateFlow<HomeUiState> = authRepository.currentUser
        .filterNotNull()
        .flatMapLatest { user ->
            val name = user.greetingName()
            val sections = tripRepository.observeTrips(user.uid).map { trips -> groupTrips(trips, LocalDate.now(clock)) }
            combine(sections, syncController.status, signOutWarning) { grouped, sync, warning ->
                HomeUiState.Content(
                    userName = name,
                    email = user.email.orEmpty(),
                    firstName = firstName(name),
                    initials = initials(name),
                    sections = grouped,
                    syncStatus = sync,
                    showSignOutWarning = warning,
                )
            }
        }
        // Grouping and sorting run off the main thread.
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    /** Set synchronously, so a double tap starts one sign out. */
    private var signingOut = false

    /**
     * Uploads what is still pending, then signs out and removes the account's data from this device. If changes
     * can't be uploaded (offline), shows the warning instead. Navigation back to Login happens in AppNavHost
     * when the session becomes null.
     */
    fun onSignOut() = signOut(force = false)

    /** "Sign out anyway": the pending changes stay on this device and upload at the next sign in here. */
    fun onConfirmSignOut() {
        signOutWarning.value = false
        signOut(force = true)
    }

    fun onDismissSignOutWarning() {
        signOutWarning.value = false
    }

    private fun signOut(force: Boolean) {
        if (signingOut) return
        signingOut = true
        viewModelScope.launch {
            try {
                if (syncController.signOut(force) == SignOutResult.NEEDS_CONFIRMATION) signOutWarning.value = true
            } finally {
                signingOut = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(
                    container.tripRepository,
                    container.authRepository,
                    container.syncController,
                    container.clock,
                )
            }
        }
    }
}
