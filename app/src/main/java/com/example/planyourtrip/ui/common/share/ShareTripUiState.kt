package com.example.planyourtrip.ui.common.share

data class ShareTripUiState(
    /** The "Include costs" dialog; null = closed. */
    val dialog: ShareDialogState? = null,
    /** A created PDF waiting to be handed to the share sheet (one-shot, cleared with `onShareLaunched`). */
    val pendingShare: SharedPdf? = null,
)

data class ShareDialogState(
    val tripId: Long,
    val includeCosts: Boolean = true,
    /** The PDF is being created: the button shows a spinner and the dialog can't be closed. */
    val isCreating: Boolean = false,
    /** The last attempt failed: a short message and "Try again". */
    val failed: Boolean = false,
)
