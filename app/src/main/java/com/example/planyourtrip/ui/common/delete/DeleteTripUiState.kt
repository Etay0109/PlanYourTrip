package com.example.planyourtrip.ui.common.delete

data class DeleteTripUiState(
    /** The "Delete this trip?" dialog; null = closed. */
    val dialog: DeleteDialogState? = null,
    /** One-shot snackbar message, cleared with `onMessageShown`. */
    val message: DeleteTripMessage? = null,
)

data class DeleteDialogState(
    val tripId: Long,
    /** Named in the dialog's text. */
    val destination: String,
    /** The delete runs: the buttons are disabled and the dialog can't be closed. */
    val isDeleting: Boolean = false,
)

enum class DeleteTripMessage { DELETED, FAILED }
