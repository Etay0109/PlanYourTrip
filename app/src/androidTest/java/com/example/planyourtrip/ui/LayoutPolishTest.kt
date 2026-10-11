package com.example.planyourtrip.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.TripStatus
import com.example.planyourtrip.ui.common.components.DestinationTitleLayout
import com.example.planyourtrip.ui.common.components.DestinationTitleLayoutKey
import com.example.planyourtrip.ui.common.components.TripDatesRow
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.home.ActiveTrip
import com.example.planyourtrip.ui.home.components.NextTripCard
import com.example.planyourtrip.ui.home.components.PastTripCard
import com.example.planyourtrip.ui.home.components.UpcomingTripCard
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.tripdetails.components.TripHeader
import com.example.planyourtrip.ui.tripdetails.members.InviteSheetActions
import com.example.planyourtrip.ui.tripdetails.members.InviteSheetContent
import com.example.planyourtrip.ui.tripdetails.members.InviteUiState
import com.example.planyourtrip.ui.tripdetails.members.MemberEntry
import com.example.planyourtrip.ui.tripdetails.members.MembersCard
import com.example.planyourtrip.ui.tripdetails.members.MembersSheetMode
import com.example.planyourtrip.ui.tripdetails.members.MembersState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * The layout rules of the 2026-10 polish pass, on a small phone (320dp, font scale 1.5) and a large one (448dp):
 * trip dates on one line, "Traveling together" never wrapping, one "Members" title in the members sheet. Each case
 * also saves a picture to the app's files/layout-checks/ for a visual check.
 */
@RunWith(AndroidJUnit4::class)
class LayoutPolishTest {

    @get:Rule
    val rule = createComposeRule()

    private val longDestination = "Llanfairpwllgwyngyll and the Isle of Anglesey, United Kingdom"
    private val trip = Trip(
        1, longDestination, LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 3), TripColor.SKY, countryCode = "GB",
    )
    private val members = MembersState(
        me = "me",
        members = listOf(
            MemberEntry(Person.You("me"), isOwner = true, joinedOn = null),
            MemberEntry(
                Person.Member("m", "Noa Levinson-Abramovitz Cohen", TravelerColor.ROSE),
                isOwner = false,
                joinedOn = LocalDate.of(2026, 10, 9),
            ),
        ),
        isOwner = true,
    )

    /** What TalkBack reads: the whole line ("6 days" has a non-breaking space). */
    private fun dates(range: String, days: Int) = "$range\u00A0· $days\u00A0days"

    /** Smallest supported (320dp, large font), small phones (320 / 360dp), and the emulator's 448dp. */
    private val sizes = listOf(
        Size(320.dp, 1.5f), Size(320.dp, 1f), Size(360.dp, 1.3f), Size(448.dp, 1f), Size(360.dp, 1f),
    )

    data class Size(val width: Dp, val fontScale: Float) {
        override fun toString() = "${width.value.toInt()}dp-x$fontScale"
    }

    private fun show(size: Size, content: @Composable () -> Unit) {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, size.fontScale)) {
                PlanYourTripTheme(darkTheme = false) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        Box(Modifier.width(size.width).padding(20.dp)) { content() }
                    }
                }
            }
        }
    }

    /** At most one line of [lineHeightSp] at the size's font scale (with room for the icon and padding). */
    private fun SemanticsNodeInteraction.assertOneLine(size: Size, lineHeightSp: Float) {
        val height = fetchSemanticsNode().boundsInRoot.height
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val oneLine = lineHeightSp * size.fontScale * density
        assertTrue("height $height px is more than one line ($oneLine px) at $size", height < oneLine * 1.5f)
    }

    /** Far too narrow for the whole line: the length goes first, then the dates get smaller; never two lines. */
    @Test
    fun datesRow_tooNarrow_staysOnOneLine() {
        val size = Size(180.dp, 1.5f)
        show(size) {
            TripDatesRow(
                LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 3), currentYear = 2026,
                color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge,
            )
        }
        rule.onNodeWithText(dates("28 Nov – 3 Dec", 6), useUnmergedTree = true)
            .assertOneLine(size, lineHeightSp = 24f)
        save("dates_row_$size")
    }

    @Test
    fun upcomingCard_longDestination_datesOnOneLine_smallPhone() = upcomingCard(sizes[0])

    @Test
    fun upcomingCard_largePhone() = upcomingCard(sizes[3])

    @Test
    fun upcomingCard_smallPhone() = upcomingCard(sizes[4])

    @Test
    fun upcomingCard_smallPhoneLargeFont() = upcomingCard(sizes[2])

    private fun upcomingCard(size: Size) {
        show(size) {
            UpcomingTripCard(
                ActiveTrip(trip, TripStatus.InDays(48)), onClick = {}, onShare = {}, onDelete = {},
                currentYear = 2026,
            )
        }
        rule.onNodeWithText(dates("28 Nov – 3 Dec", 6), useUnmergedTree = true)
            .assertOneLine(size, lineHeightSp = 24f)
        save("upcoming_$size")
    }

    @Test
    fun nextCard_longDestination_datesOnOneLine_smallPhone() = nextCard(sizes[0])

    @Test
    fun nextCard_largePhone() = nextCard(sizes[3])

    @Test
    fun nextCard_smallPhone() = nextCard(sizes[4])

    @Test
    fun nextCard_smallPhoneLargeFont() = nextCard(sizes[2])

    private fun nextCard(size: Size) {
        show(size) {
            NextTripCard(
                ActiveTrip(trip, TripStatus.InDays(48)), onClick = {}, onShare = {}, onDelete = {},
                currentYear = 2026,
            )
        }
        rule.onNodeWithText(dates("28 Nov – 3 Dec", 6), useUnmergedTree = true)
            .assertOneLine(size, lineHeightSp = 24f)
        save("next_$size")
    }

    @Test
    fun header_otherYear_keepsTheYear_onOneLine_smallPhone() = header(sizes[0])

    @Test
    fun header_largePhone() = header(sizes[3])

    @Test
    fun header_smallPhone() = header(sizes[4])

    private fun header(size: Size) {
        val nextYear = trip.copy(startDate = LocalDate.of(2027, 12, 28), endDate = LocalDate.of(2028, 1, 3))
        show(size) {
            TripHeader(
                nextYear, TripStatus.InDays(400), onBack = {}, onEdit = {}, onShare = {}, onDelete = {},
                currentYear = 2026,
            )
        }
        rule.onNodeWithText(dates("28 Dec 2027 – 3 Jan 2028", 7), useUnmergedTree = true)
            .assertOneLine(size, lineHeightSp = 24f)
        save("header_$size")
    }

    @Test
    fun membersCard_titleOnOneLine_smallPhone() = membersCard(sizes[0])

    @Test
    fun membersCard_titleOnOneLine_smallPhoneNormalFont() = membersCard(sizes[1])

    @Test
    fun membersCard_titleOnOneLine_mediumPhone() = membersCard(sizes[2])

    @Test
    fun membersCard_largePhone_fullInviteButton() {
        membersCard(sizes[3])
        rule.onNodeWithText("Invite", useUnmergedTree = true).assertExists()
    }

    private fun membersCard(size: Size) {
        show(size) { MembersCard(members, onShowMembers = {}, onInvite = {}) }
        val title = rule.onNodeWithText("Traveling together", useUnmergedTree = true)
        title.assertOneLine(size, lineHeightSp = 28f)
        // Smaller if needed, but never cut with "…".
        val layouts = mutableListOf<TextLayoutResult>()
        title.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertFalse("title cut at $size", layouts.single().isLineEllipsized(0))
        // "Invite" is always there: as a labeled button or a round icon button.
        val invite = rule.onAllNodesWithText("Invite", useUnmergedTree = true).fetchSemanticsNodes().size +
            rule.onAllNodesWithContentDescription("Invite", useUnmergedTree = true).fetchSemanticsNodes().size
        assertTrue("no Invite button at $size", invite >= 1)
        save("members_card_$size")
    }

    @Test
    fun membersSheet_hasOneMembersTitle_smallPhone() = membersSheet(sizes[0])

    @Test
    fun membersSheet_largePhone() = membersSheet(sizes[3])

    @Test
    fun membersCard_titleOnOneLine_smallPhone360() = membersCard(sizes[4])

    private fun membersSheet(size: Size) {
        show(size) {
            InviteSheetContent(MembersSheetMode.MEMBERS, InviteUiState(), members, "London", InviteSheetActions.None)
        }
        assertEquals(1, rule.onAllNodesWithText("Members").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Members of", substring = true).fetchSemanticsNodes().size)
        save("members_sheet_$size")
    }

    // Destination titles at 360dp: never broken inside a word (shrink a little, then a narrower countdown box, then
    // wrap between words or end with "…").

    private val london = Trip(
        2, "London, United Kingdom", LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 13), TripColor.SKY,
        countryCode = "GB",
    )
    private val longWord = london.copy(destination = "Llanfairpwllgwyngyll, United Kingdom")
    private val twoWords = london.copy(destination = "Buenos Aires, Argentina", countryCode = "AR")
    private val phone360 = sizes[4]

    private fun titleLayout(): DestinationTitleLayout = rule
        .onNode(SemanticsMatcher.keyIsDefined(DestinationTitleLayoutKey), useUnmergedTree = true)
        .fetchSemanticsNode().config[DestinationTitleLayoutKey]

    private fun assertLondon() {
        val layout = titleLayout()
        assertEquals("London on one line", 1, layout.lineCount)
        assertFalse("London cut", layout.ellipsized)
    }

    private fun assertLongWord() {
        val layout = titleLayout()
        assertEquals("a single long word stays on one line", 1, layout.lineCount)
        assertFalse(layout.brokeInsideWord)
    }

    private fun assertTwoWords() {
        val layout = titleLayout()
        assertFalse("broken inside a word: $layout", layout.brokeInsideWord)
        assertFalse("two words cut: $layout", layout.ellipsized)
        assertTrue(layout.lineCount <= 2)
    }

    private fun next(trip: Trip, status: TripStatus = TripStatus.Now) = show(phone360) {
        NextTripCard(ActiveTrip(trip, status), onClick = {}, onShare = {}, onDelete = {}, currentYear = 2026)
    }

    private fun upcoming(trip: Trip) = show(phone360) {
        UpcomingTripCard(
            ActiveTrip(trip, TripStatus.InDays(8)), onClick = {}, onShare = {}, onDelete = {}, currentYear = 2026,
        )
    }

    private fun past(trip: Trip) = show(phone360) { PastTripCard(trip, onClick = {}, onShare = {}, onDelete = {}) }

    private fun header(trip: Trip) = show(phone360) {
        TripHeader(trip, TripStatus.Now, onBack = {}, onEdit = {}, onShare = {}, onDelete = {}, currentYear = 2026)
    }

    @Test
    fun title360_next_london() { next(london); assertLondon(); save("title_next_london") }

    @Test
    fun title360_next_london_countdown() {
        next(london, TripStatus.InDays(48))
        assertLondon()
        save("title_next_london_days")
    }

    @Test
    fun title360_next_longWord() { next(longWord); assertLongWord(); save("title_next_long_word") }

    @Test
    fun title360_next_twoWords() { next(twoWords); assertTwoWords(); save("title_next_two_words") }

    @Test
    fun title360_upcoming_london() { upcoming(london); assertLondon(); save("title_upcoming_london") }

    @Test
    fun title360_upcoming_longWord() { upcoming(longWord); assertLongWord(); save("title_upcoming_long_word") }

    @Test
    fun title360_upcoming_twoWords() { upcoming(twoWords); assertTwoWords(); save("title_upcoming_two_words") }

    @Test
    fun title360_past_london() { past(london); assertLondon(); save("title_past_london") }

    @Test
    fun title360_past_longWord() { past(longWord); assertLongWord(); save("title_past_long_word") }

    @Test
    fun title360_past_twoWords() { past(twoWords); assertTwoWords(); save("title_past_two_words") }

    @Test
    fun title360_header_london() { header(london); assertLondon(); save("title_header_london") }

    @Test
    fun title360_header_longWord() { header(longWord); assertLongWord(); save("title_header_long_word") }

    @Test
    fun title360_header_twoWords() { header(twoWords); assertTwoWords(); save("title_header_two_words") }

    private fun save(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "layout-checks")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
