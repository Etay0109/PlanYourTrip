package com.example.planyourtrip.ui.common.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.sync.SignOutResult
import com.example.planyourtrip.data.sync.SyncController
import com.example.planyourtrip.data.sync.SyncStatus
import com.example.planyourtrip.data.user.greetingName
import com.example.planyourtrip.data.user.initials
import com.example.planyourtrip.ui.common.components.AccountActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the profile menu shows (Home avatar). */
data class AccountState(
    /** Display name, or the email prefix when there is no name (see `greetingName()`). */
    val userName: String,
    val email: String,
    val initials: String,
    val syncStatus: SyncStatus = SyncStatus.SAVED,
    /** Changes still wait to upload and there is no connection: ask before signing out. */
    val showSignOutWarning: Boolean = false,
)

/**
 * The profile menu: the signed-in user, the sync status and "Sign out". Used by every screen that shows the menu,
 * so they all sign out the same way. Navigation back to Login happens in AppNavHost when the session becomes null.
 */
class AccountViewModel(
    authRepository: AuthRepository,
    private val syncController: SyncController,
) : ViewModel() {

    private val signOutWarning = MutableStateFlow(false)

    /** Null while nobody is signed in (nothing is drawn). */
    val state: StateFlow<AccountState?> =
        combine(authRepository.currentUser, syncController.status, signOutWarning) { user, sync, warning ->
            user?.let {
                val name = it.greetingName()
                AccountState(
                    userName = name,
                    email = it.email.orEmpty(),
                    initials = initials(name),
                    syncStatus = sync,
                    showSignOutWarning = warning,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Set synchronously, so a double tap starts one sign out. */
    private var signingOut = false

    /**
     * Uploads what is still pending, then signs out and removes the account's data from this device. If changes
     * can't be uploaded (offline), shows the warning instead.
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
                AccountViewModel(container.authRepository, container.syncController)
            }
        }
    }
}

/** The menu's callbacks, for [com.example.planyourtrip.ui.common.components.AccountButton]. */
fun AccountViewModel.actions() = AccountActions(
    onSignOut = ::onSignOut,
    onConfirmSignOut = ::onConfirmSignOut,
    onDismissSignOutWarning = ::onDismissSignOutWarning,
)
