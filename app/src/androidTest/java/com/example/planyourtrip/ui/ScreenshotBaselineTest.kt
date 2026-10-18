package com.example.planyourtrip.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * Renders every `@Preview` at its preview size and font scale and saves it to the app's files/screenshots/. Run it
 * before and after a refactor that must not change the UI and compare the pictures (identical bitmaps give identical
 * PNG files). Animations are frozen at a fixed time, so each run draws the same frame.
 */
@RunWith(Parameterized::class)
class ScreenshotBaselineTest(private val shot: Shot) {

    @get:Rule
    val rule = createComposeRule()

    class Shot(
        val name: String,
        val width: Dp,
        /** Null: as tall as the content. */
        val height: Dp?,
        val fontScale: Float,
        val content: @Composable () -> Unit,
    ) {
        override fun toString() = name
    }

    @Test
    fun capture() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, shot.fontScale)) {
                val size = Modifier.width(shot.width).let { if (shot.height != null) it.height(shot.height) else it }
                Box(size) { shot.content() }
            }
        }
        rule.mainClock.advanceTimeBy(FROZEN_AT_MILLIS)
        rule.waitForIdle()
        val dialogs = rule.onAllNodes(isDialog()).fetchSemanticsNodes()
        val node = if (dialogs.isEmpty()) rule.onRoot() else rule.onNode(isDialog())
        val bitmap = node.captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screenshots")
        dir.mkdirs()
        File(dir, "${shot.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        private const val FROZEN_AT_MILLIS = 2_000L

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun shots(): List<Shot> = listOf(
        Shot("home_HomePreview", 411.dp, 891.dp, 1f) { com.example.planyourtrip.ui.home.HomePreview() },
        Shot("home_HomeNarrowPreview", 320.dp, 800.dp, 1.5f) { com.example.planyourtrip.ui.home.HomeNarrowPreview() },
        Shot("home_HomeDarkPreview", 411.dp, 891.dp, 1f) { com.example.planyourtrip.ui.home.HomeDarkPreview() },
        Shot("home_HomeEmptyPreview", 411.dp, 891.dp, 1f) { com.example.planyourtrip.ui.home.HomeEmptyPreview() },
        Shot("join_JoinTripFoundPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.home.join.JoinTripFoundPreview() },
        Shot("join_JoinTripNarrowPreview", 320.dp, null, 1.5f) { com.example.planyourtrip.ui.home.join.JoinTripNarrowPreview() },
        Shot("join_JoinTripInvalidPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.home.join.JoinTripInvalidPreview() },
        Shot("auth_AuthSignInPreview", 412.dp, 915.dp, 1f) { com.example.planyourtrip.ui.auth.AuthSignInPreview() },
        Shot("auth_AuthSignUpPreview", 412.dp, 915.dp, 1f) { com.example.planyourtrip.ui.auth.AuthSignUpPreview() },
        Shot("auth_AuthSignUpDarkPreview", 412.dp, 915.dp, 1f) { com.example.planyourtrip.ui.auth.AuthSignUpDarkPreview() },
        Shot("auth_AuthSignUpSmallPreview", 320.dp, 640.dp, 1.5f) { com.example.planyourtrip.ui.auth.AuthSignUpSmallPreview() },
        Shot("delete_DeleteTripDialogPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.common.delete.DeleteTripDialogPreview() },
        Shot("delete_LeaveTripDialogPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.common.delete.LeaveTripDialogPreview() },
        Shot("delete_DeleteTripDialogHebrewPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.common.delete.DeleteTripDialogHebrewPreview() },
        Shot("share_ShareTripDialogPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.common.share.ShareTripDialogPreview() },
        Shot("share_ShareTripDialogFailedPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.common.share.ShareTripDialogFailedPreview() },
        Shot("tripdetails_OverviewPreview", 411.dp, 891.dp, 1f) { com.example.planyourtrip.ui.tripdetails.OverviewPreview() },
        Shot("tripdetails_OverviewNoBudgetPreview", 411.dp, 891.dp, 1f) { com.example.planyourtrip.ui.tripdetails.OverviewNoBudgetPreview() },
        Shot("tripdetails_OverviewShekelPreview", 411.dp, 891.dp, 1f) { com.example.planyourtrip.ui.tripdetails.OverviewShekelPreview() },
        Shot("tripdetails_OverviewRateUnavailablePreview", 411.dp, 891.dp, 1f) { com.example.planyourtrip.ui.tripdetails.OverviewRateUnavailablePreview() },
        Shot("tripdetails_OverviewJpyPreview", 411.dp, 891.dp, 1f) { com.example.planyourtrip.ui.tripdetails.OverviewJpyPreview() },
        Shot("tripdetails_BudgetPreview", 411.dp, 1900.dp, 1f) { com.example.planyourtrip.ui.tripdetails.BudgetPreview() },
        Shot("tripdetails_BudgetOverPreview", 411.dp, 1400.dp, 1f) { com.example.planyourtrip.ui.tripdetails.BudgetOverPreview() },
        Shot("tripdetails_BudgetDarkPreview", 411.dp, 1900.dp, 1f) { com.example.planyourtrip.ui.tripdetails.BudgetDarkPreview() },
        Shot("tripdetails_BudgetNarrowPreview", 320.dp, 2400.dp, 1.5f) { com.example.planyourtrip.ui.tripdetails.BudgetNarrowPreview() },
        Shot("tripdetails_BudgetEmptyPreview", 411.dp, 891.dp, 1f) { com.example.planyourtrip.ui.tripdetails.BudgetEmptyPreview() },
        Shot("tripdetails_BudgetOnlyMePreview", 411.dp, 1900.dp, 1f) { com.example.planyourtrip.ui.tripdetails.BudgetOnlyMePreview() },
        Shot("tripdetails_BudgetSettledPreview", 411.dp, 1900.dp, 1f) { com.example.planyourtrip.ui.tripdetails.BudgetSettledPreview() },
        Shot("tripdetails_OverviewNarrowPreview", 320.dp, 1000.dp, 1.5f) { com.example.planyourtrip.ui.tripdetails.OverviewNarrowPreview() },
        Shot("tripdetails_PlanPreview", 411.dp, 2100.dp, 1f) { com.example.planyourtrip.ui.tripdetails.PlanPreview() },
        Shot("tripdetails_PlanDarkPreview", 411.dp, 2100.dp, 1f) { com.example.planyourtrip.ui.tripdetails.PlanDarkPreview() },
        Shot("tripdetails_OverviewDarkPreview", 411.dp, 1500.dp, 1f) { com.example.planyourtrip.ui.tripdetails.OverviewDarkPreview() },
        Shot("tripdetails_PlanEmptyDayPreview", 411.dp, 1000.dp, 1f) { com.example.planyourtrip.ui.tripdetails.PlanEmptyDayPreview() },
        Shot("tripdetails_PrepPreview", 411.dp, 1700.dp, 1f) { com.example.planyourtrip.ui.tripdetails.PrepPreview() },
        Shot("tripdetails_PrepDarkPreview", 411.dp, 1700.dp, 1f) { com.example.planyourtrip.ui.tripdetails.PrepDarkPreview() },
        Shot("tripdetails_PrepEmptyPreview", 411.dp, 1300.dp, 1f) { com.example.planyourtrip.ui.tripdetails.PrepEmptyPreview() },
        Shot("tripdetails_PrepNarrowPreview", 320.dp, 2200.dp, 1.5f) { com.example.planyourtrip.ui.tripdetails.PrepNarrowPreview() },
        Shot("tripdetails_PlanOutsidePreview", 411.dp, 1200.dp, 1f) { com.example.planyourtrip.ui.tripdetails.PlanOutsidePreview() },
        Shot("tripdetails_PlanNarrowPreview", 320.dp, 1600.dp, 1.5f) { com.example.planyourtrip.ui.tripdetails.PlanNarrowPreview() },
        Shot("tripdetails_OverviewLongTripPreview", 411.dp, 1400.dp, 1f) { com.example.planyourtrip.ui.tripdetails.OverviewLongTripPreview() },
        Shot("assistant_EmptyPreview", 411.dp, 891.dp, 1f) { com.example.planyourtrip.ui.tripdetails.assistant.EmptyPreview() },
        Shot("assistant_ConversationPreview", 411.dp, 1500.dp, 1f) { com.example.planyourtrip.ui.tripdetails.assistant.ConversationPreview() },
        Shot("assistant_TypingDarkPreview", 411.dp, 1500.dp, 1f) { com.example.planyourtrip.ui.tripdetails.assistant.TypingDarkPreview() },
        Shot("assistant_ErrorPreview", 411.dp, 1500.dp, 1f) { com.example.planyourtrip.ui.tripdetails.assistant.ErrorPreview() },
        Shot("assistant_OutsideNarrowPreview", 320.dp, 1800.dp, 1.5f) { com.example.planyourtrip.ui.tripdetails.assistant.OutsideNarrowPreview() },
        Shot("expense_ExpenseFormForeignPreview", 411.dp, 900.dp, 1f) { com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormForeignPreview() },
        Shot("expense_ExpenseFormForeignDarkPreview", 411.dp, 900.dp, 1f) { com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormForeignDarkPreview() },
        Shot("expense_ExpenseFormNoRatePreview", 411.dp, 900.dp, 1f) { com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormNoRatePreview() },
        Shot("expense_ExpenseFormShekelPreview", 411.dp, 900.dp, 1f) { com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormShekelPreview() },
        Shot("expense_ExpenseFormSplitErrorPreview", 411.dp, 900.dp, 1f) { com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormSplitErrorPreview() },
        Shot("expense_ExpenseFormNarrowPreview", 320.dp, 1100.dp, 1.5f) { com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormNarrowPreview() },
        Shot("activity_ActivityFormPreview", 411.dp, 1100.dp, 1f) { com.example.planyourtrip.ui.tripdetails.activity.ActivityFormPreview() },
        Shot("activity_ActivityFormNarrowPreview", 320.dp, 1400.dp, 1.5f) { com.example.planyourtrip.ui.tripdetails.activity.ActivityFormNarrowPreview() },
        Shot("activity_ActivityFormDarkPreview", 411.dp, 1100.dp, 1f) { com.example.planyourtrip.ui.tripdetails.activity.ActivityFormDarkPreview() },
        Shot("components_LargeAmountPreview", 320.dp, null, 1.5f) { com.example.planyourtrip.ui.tripdetails.components.LargeAmountPreview() },
        Shot("components_JpyPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.tripdetails.components.JpyPreview() },
        Shot("components_ArsPreview", 320.dp, null, 1.5f) { com.example.planyourtrip.ui.tripdetails.components.ArsPreview() },
        Shot("components_OverviewMapIllustrationPreview", 180.dp, 120.dp, 1f) { com.example.planyourtrip.ui.tripdetails.components.OverviewMapIllustrationPreview() },
        Shot("components_OverviewMapIllustrationDarkPreview", 180.dp, 120.dp, 1f) { com.example.planyourtrip.ui.tripdetails.components.OverviewMapIllustrationDarkPreview() },
        Shot("members_InviteSheetPreview", 411.dp, 1200.dp, 1f) { com.example.planyourtrip.ui.tripdetails.members.InviteSheetPreview() },
        Shot("members_InviteSheetNarrowPreview", 320.dp, 1400.dp, 1.5f) { com.example.planyourtrip.ui.tripdetails.members.InviteSheetNarrowPreview() },
        Shot("members_MembersSheetMemberPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.tripdetails.members.MembersSheetMemberPreview() },
        Shot("members_MembersCardPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.tripdetails.members.MembersCardPreview() },
        Shot("members_MembersCardNarrowPreview", 320.dp, null, 1.5f) { com.example.planyourtrip.ui.tripdetails.members.MembersCardNarrowPreview() },
        Shot("tripform_TripFormPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.tripform.TripFormPreview() },
        Shot("tripform_TripFormSuggestionsPreview", 411.dp, null, 1f) { com.example.planyourtrip.ui.tripform.TripFormSuggestionsPreview() },
        Shot("tripform_TripFormCurrencyPreview", 320.dp, null, 1.5f) { com.example.planyourtrip.ui.tripform.TripFormCurrencyPreview() },
        )
    }
}
