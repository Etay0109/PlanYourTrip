package com.example.planyourtrip.ui.home

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        /** Display name, or the email prefix when there is no name (see `greetingName()`). */
        val userName: String,
        val firstName: String,
        val sections: TripSections,
    ) : HomeUiState
}
