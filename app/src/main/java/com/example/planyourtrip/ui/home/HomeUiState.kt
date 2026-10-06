package com.example.planyourtrip.ui.home

import com.example.planyourtrip.data.sync.SyncStatus

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        /** Display name, or the email prefix when there is no name (see `greetingName()`). */
        val userName: String,
        val email: String,
        val firstName: String,
        val initials: String,
        val sections: TripSections,
        /** Shown in the profile menu. */
        val syncStatus: SyncStatus = SyncStatus.SAVED,
        /** Changes still wait to upload and there is no connection: ask before signing out. */
        val showSignOutWarning: Boolean = false,
    ) : HomeUiState
}
