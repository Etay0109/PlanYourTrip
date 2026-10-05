package com.example.planyourtrip.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.auth.AuthUser
import kotlinx.coroutines.flow.StateFlow

/** App-level session state used by the navigation graph. */
class SessionViewModel(repository: AuthRepository) : ViewModel() {

    val currentUser: StateFlow<AuthUser?> = repository.currentUser

    /** Read once: the auth backend restores a saved session synchronously, so no splash screen is needed. */
    val startsSignedIn: Boolean = repository.currentUser.value != null

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { SessionViewModel(appContainer().authRepository) }
        }
    }
}
