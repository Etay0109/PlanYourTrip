package com.example.planyourtrip.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.planyourtrip.appContainer
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.reminder.ReminderLink
import com.example.planyourtrip.data.reminder.ReminderPermissionPrompt
import com.example.planyourtrip.data.reminder.ReminderTrips
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** App-level session state used by the navigation graph. */
class SessionViewModel(
    private val repository: AuthRepository,
    private val reminderTrips: ReminderTrips,
    private val permissionPrompt: ReminderPermissionPrompt,
) : ViewModel() {

    val currentUser: StateFlow<AuthUser?> = repository.currentUser

    /** Read once: the auth backend restores a saved session synchronously, so no splash screen is needed. */
    val startsSignedIn: Boolean = repository.currentUser.value != null

    /** Ask for the notification permission now (after creating or joining a future trip; once). */
    val permissionRequests: Flow<Unit> = permissionPrompt.requests

    fun onPermissionAsked() {
        permissionPrompt.onAsked()
    }

    /**
     * The local id of a tapped reminder's trip, or null when another account (or nobody) is signed in or the trip
     * is gone: then the app simply opens as usual.
     */
    suspend fun tripIdFor(link: ReminderLink): Long? {
        val uid = repository.currentUser.value?.uid ?: return null
        if (uid != link.uid) return null
        return reminderTrips.trip(link.tripUuid, uid)?.id
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SessionViewModel(container.authRepository, container.reminderTrips, container.reminderPermissionPrompt)
            }
        }
    }
}
