package com.example.planyourtrip.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.data.user.firstName
import com.example.planyourtrip.data.user.greetingName
import com.example.planyourtrip.ui.common.WhileUiSubscribed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    tripRepository: TripRepository,
    authRepository: AuthRepository,
    private val clock: Clock,
    /** Where trips are grouped and sorted; tests pass their test dispatcher. */
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /** The signed-in user's trips; switches automatically when the account changes. The profile menu is AccountViewModel. */
    val uiState: StateFlow<HomeUiState> = authRepository.currentUser
        .filterNotNull()
        .flatMapLatest { user ->
            val name = user.greetingName()
            tripRepository.observeTrips(user.uid).map { trips ->
                HomeUiState.Content(
                    userName = name,
                    firstName = firstName(name),
                    sections = groupTrips(trips, LocalDate.now(clock)),
                )
            }
        }
        // Grouping and sorting run off the main thread.
        .flowOn(computeDispatcher)
        .stateIn(viewModelScope, WhileUiSubscribed, HomeUiState.Loading)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(
                    container.tripRepository,
                    container.authRepository,
                    container.clock,
                )
            }
        }
    }
}
