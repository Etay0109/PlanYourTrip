package com.example.planyourtrip.ui.tripdetails.members

import com.example.planyourtrip.data.sharing.Invite
import java.time.LocalDate

/** The trip's members sheet: opened from the "Traveling together" card ([MEMBERS]) or with "Invite" ([INVITE]). */
enum class MembersSheetMode { MEMBERS, INVITE }

/** The invite code part of the sheet (owner only). */
sealed interface InviteCodeState {
    data object Loading : InviteCodeState

    /** The open code; [expiresOn] is the last day it works, in this device's time zone. */
    data class Ready(val invite: Invite, val expiresOn: LocalDate, val isCancelling: Boolean = false) : InviteCodeState

    /** The owner cancelled it: "Create a new code". */
    data object Cancelled : InviteCodeState

    /** The trip has 10 members: no invite can be made. */
    data object Full : InviteCodeState

    /** Offline or the request failed: "Try again". */
    data object Failed : InviteCodeState
}

/** One-shot snackbar messages of the sheet; cleared with onMessageShown. */
sealed interface InviteMessage {
    data class Removed(val name: String?) : InviteMessage

    data object RemoveFailed : InviteMessage

    data object CancelFailed : InviteMessage
}

data class InviteUiState(
    /** Null = the sheet is closed. */
    val sheet: MembersSheetMode? = null,
    val code: InviteCodeState = InviteCodeState.Loading,
    /** The member the owner is asked to confirm removing ("Remove Noa?"). */
    val removing: MemberEntry? = null,
    /** The removal runs: the dialog's buttons are disabled and a second tap does nothing. */
    val isRemoving: Boolean = false,
    val message: InviteMessage? = null,
)
