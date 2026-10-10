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
import com.example.planyourtrip.ui.common.WhileUiSubscribed
import com.example.planyourtrip.ui.common.components.AccountActions
import kotlinx.coroutines.flow.MutableStateFlow
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
    /** Epoch millis of the last successful full sync on this device (shown with "Sync problem"); null = never. */
    val lastSyncedAt: Long? = null,
    /** Signing out would keep the account's data on this device: ask first, saying why. */
    val signOutWarning: SignOutWarning? = null,
    /** A retry ("tap to retry") is running. */
    val isRetrying: Boolean = false,
) {
    val showSignOutWarning: Boolean get() = signOutWarning != null
}

/** Why signing out now would keep the account's data on this device. */
enum class SignOutWarning {
    /** Changes still wait to upload. */
    PENDING_CHANGES,

    /** Nothing is pending, but no full sync confirmed that the cloud holds the data. */
    NOT_CONFIRMED,
}

/**
 * The profile menu: the signed-in user, the sync status and "Sign out". Used by every screen that shows the menu,
 * so they all sign out the same way. Navigation back to Login happens in AppNavHost when the session becomes null.
 */
class AccountViewModel(
    authRepository: AuthRepository,
    private val syncController: SyncController,
) : ViewModel() {

    private val signOutWarning = MutableStateFlow<SignOutWarning?>(null)
    private val retrying = MutableStateFlow(false)

    /** Null while nobody is signed in (nothing is drawn). */
    val state: StateFlow<AccountState?> =
        combine(
            authRepository.currentUser, syncController.status, syncController.lastSyncedAt, signOutWarning, retrying,
        ) { user, sync, lastSyncedAt, warning, isRetrying ->
            user?.let {
                val name = it.greetingName()
                AccountState(
                    userName = name,
                    email = it.email.orEmpty(),
                    initials = initials(name),
                    syncStatus = sync,
                    lastSyncedAt = lastSyncedAt,
                    signOutWarning = warning,
                    isRetrying = isRetrying,
                )
            }
        }.stateIn(viewModelScope, WhileUiSubscribed, null)

    /** Set synchronously, so a double tap starts one sign out. */
    private var signingOut = false

    /**
     * Syncs, then signs out and removes the account's data from this device when it is safely in the cloud;
     * otherwise shows the warning (changes still pending, or the cloud copy not confirmed).
     */
    fun onSignOut() = signOut(force = false)

    /** "Sign out anyway": the account's data stays on this device (hidden) and syncs at the next sign in here. */
    fun onConfirmSignOut() {
        signOutWarning.value = null
        signOut(force = true)
    }

    fun onDismissSignOutWarning() {
        signOutWarning.value = null
    }

    /** "Sync problem – tap to retry": a full sync now; the status shows its result. Double taps run one. */
    fun onRetrySync() {
        if (retrying.value) return
        retrying.value = true
        viewModelScope.launch {
            try {
                syncController.refresh()
            } finally {
                retrying.value = false
            }
        }
    }

    private fun signOut(force: Boolean) {
        if (signingOut) return
        signingOut = true
        viewModelScope.launch {
            try {
                signOutWarning.value = when (syncController.signOut(force)) {
                    SignOutResult.SIGNED_OUT -> null
                    SignOutResult.NEEDS_CONFIRMATION -> SignOutWarning.PENDING_CHANGES
                    SignOutResult.NOT_CONFIRMED -> SignOutWarning.NOT_CONFIRMED
                }
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
    onRetrySync = ::onRetrySync,
)
