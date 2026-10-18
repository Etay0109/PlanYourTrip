package com.example.planyourtrip.ui.tripdetails.members

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.planyourtrip.data.sharing.Invite
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import java.time.LocalDate

private val previewInviteState = InviteUiState(
    sheet = MembersSheetMode.INVITE,
    code = InviteCodeState.Ready(Invite("K7M4XP2R", createdAt = 0), expiresOn = LocalDate.of(2026, 10, 16)),
)

@Preview(showBackground = true, heightDp = 1200)
@Composable
internal fun InviteSheetPreview() {
    PlanYourTripTheme(darkTheme = false) {
        InviteSheetContent(
            MembersSheetMode.INVITE,
            previewInviteState,
            previewMembers.copy(
                members = previewMembers.members.map {
                    if (it.isOwner) it else it.copy(joinedOn = LocalDate.of(2026, 10, 9))
                },
            ),
            "Rome, Italy",
            InviteSheetActions.None,
        )
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f, heightDp = 1400)
@Composable
internal fun InviteSheetNarrowPreview() {
    PlanYourTripTheme(darkTheme = true) {
        InviteSheetContent(MembersSheetMode.INVITE, previewInviteState, previewMembers, "Rome, Italy", InviteSheetActions.None)
    }
}

@Preview(showBackground = true)
@Composable
internal fun MembersSheetMemberPreview() {
    PlanYourTripTheme(darkTheme = false) {
        InviteSheetContent(
            MembersSheetMode.MEMBERS,
            InviteUiState(sheet = MembersSheetMode.MEMBERS),
            MembersState(
                me = "noa",
                members = listOf(
                    MemberEntry(Person.You("noa"), isOwner = false, joinedOn = LocalDate.of(2026, 10, 9)),
                    MemberEntry(
                        Person.Member("me", "Amit", com.example.planyourtrip.data.trip.TravelerColor.GREEN_TEAL, isOwner = true),
                        isOwner = true,
                        joinedOn = null,
                    ),
                ),
                isOwner = false,
            ),
            "Rome, Italy",
            InviteSheetActions.None,
        )
    }
}
