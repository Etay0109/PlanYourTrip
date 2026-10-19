package com.example.planyourtrip.ui.home.join

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.example.planyourtrip.data.sharing.InvitePreview
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import java.time.LocalDate

private val previewInvite = InvitePreview(
    code = "K7M4XP2R",
    tripUuid = "t1",
    destination = "Rome, Italy",
    startDate = LocalDate.of(2026, 10, 19),
    endDate = LocalDate.of(2026, 10, 24),
    inviterName = "Noa",
)

@Preview(showBackground = true)
@Composable
internal fun JoinTripFoundPreview() {
    PlanYourTripTheme(darkTheme = false) {
        JoinTripContent(
            state = JoinUiState(isOpen = true, code = "K7M4XP2R", lookup = CodeLookup.Found(previewInvite)),
            onCodeChange = {}, onRetry = {}, onJoin = {}, onDismiss = {}, initialCode = "K7M4XP2R",
        )
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
internal fun JoinTripNarrowPreview() {
    PlanYourTripTheme(darkTheme = false) {
        JoinTripContent(
            state = JoinUiState(isOpen = true, code = "K7M4XP2R", lookup = CodeLookup.Found(previewInvite)),
            onCodeChange = {}, onRetry = {}, onJoin = {}, onDismiss = {}, initialCode = "K7M4XP2R",
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun JoinTripInvalidPreview() {
    PlanYourTripTheme(darkTheme = true) {
        JoinTripContent(
            state = JoinUiState(isOpen = true, code = "K7M4XP2R", lookup = CodeLookup.Invalid),
            onCodeChange = {}, onRetry = {}, onJoin = {}, onDismiss = {}, initialCode = "K7M4XP2R",
        )
    }
}
