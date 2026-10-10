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
    /** The delete (or leave) runs: the buttons are disabled and the dialog can't be closed. */
    val isDeleting: Boolean = false,
    /** "Leave this trip?" instead: a shared trip I don't own (only its owner can delete it). */
    val leave: Boolean = false,
)

enum class DeleteTripMessage {
    DELETED,
    FAILED,

    /** "You left the trip". */
    LEFT,

    /** Couldn't leave (offline or refused); nothing changed. */
    LEAVE_FAILED,
}
