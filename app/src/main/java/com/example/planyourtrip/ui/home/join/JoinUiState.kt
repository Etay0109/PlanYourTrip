package com.example.planyourtrip.ui.home.join

import com.example.planyourtrip.data.sharing.InvitePreview

/** The Home "Join a trip" sheet. The typed code itself is Compose state (see JoinTripSheet). */
data class JoinUiState(
    val isOpen: Boolean = false,
    /** The code the [lookup] is about (8 valid characters), or "" while fewer are typed. */
    val code: String = "",
    val lookup: CodeLookup = CodeLookup.Incomplete,
    /** The join runs: the button shows a spinner, the sheet can't be closed, a second tap does nothing. */
    val isJoining: Boolean = false,
    /** Why the last join didn't happen; cleared when the code changes. */
    val error: JoinError? = null,
    /** One-shot: the destination of the trip just joined ("You joined Rome, Italy"); cleared with onJoinedShown. */
    val joinedDestination: String? = null,
) {
    /** "Join trip" is enabled only once a valid code shows its trip. */
    val canJoin: Boolean get() = lookup is CodeLookup.Found && !isJoining
}

sealed interface CodeLookup {
    /** Fewer than 8 characters. */
    data object Incomplete : CodeLookup

    data object Checking : CodeLookup

    /** An open invite: what it shows before joining. */
    data class Found(val preview: InvitePreview) : CodeLookup

    /** Wrong, expired, used or cancelled: one state for all, so a code reveals nothing. */
    data object Invalid : CodeLookup

    /** Offline or the request failed ("Try again"). */
    data object Failed : CodeLookup
}

enum class JoinError { INVALID_CODE, ALREADY_MEMBER, TRIP_FULL, FAILED }
