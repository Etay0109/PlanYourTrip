package com.example.planyourtrip.ui.tripdetails.split

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeExpenseRepository
import com.example.planyourtrip.FakeSettlementRepository
import com.example.planyourtrip.FakeTravelerRepository
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.ME
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.InMemoryMergeSuggestionStore
import com.example.planyourtrip.data.trip.MergeBlock
import com.example.planyourtrip.data.trip.MergeSuggestionStore
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.data.trip.TripMember
import com.example.planyourtrip.data.trip.TripMemberRepository
import com.example.planyourtrip.ui.common.split.MergeSuggestion
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.common.split.Transfer
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Budget tab's "Yarden Tal Ronen joined this trip. Merge with the traveler 'Yarden'?" (the London trip on the
 * phone): I (u1) shared it, Yarden joined with her account, and I had added her by hand as "Yarden" before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SplitMergeViewModelTest {

    private val today = LocalDate.of(2026, 10, 10)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val dispatcher = UnconfinedTestDispatcher()
    private val auth = FakeAuthRepository(AuthUser(ME, "Etay Zahavi", "etay@example.com"))
    private val her = "yarden-uid"

    private val london = Trip(
        1, "London", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5), TripColor.SKY,
        ownerId = ME, memberIds = listOf(ME, her), uuid = "london",
    )
    private val yarden = Traveler(1, 1, "yarden-name", "Yarden", TravelerColor.ROSE)
    private val members = object : TripMemberRepository {
        override fun observeMembers(tripId: Long, ownerId: String): Flow<List<TripMember>> =
            flowOf(listOf(TripMember(ME, "Etay Zahavi", 1, false), TripMember(her, "Yarden Tal Ronen", 2, false)))
    }

    private val hotel = Expense(1, 1, "Marriott", 30_000, ExpenseCategory.HOTELS, today, paidBy = ME, splitBetween = listOf(ME, yarden.uuid))
    private val zara = Expense(2, 1, "Zara", 25_000, ExpenseCategory.SHOPPING, today, paidBy = yarden.uuid, splitBetween = listOf(ME, yarden.uuid))
    /** Hotel: she owes me 150; Zara: I owe her 125; she paid back 10: she still owes me 15. */
    private val paidBack = Settlement(1, 1, "s1", yarden.uuid, ME, 1_000, today)

    private lateinit var trips: FakeTripRepository
    private lateinit var expenses: FakeExpenseRepository
    private lateinit var travelers: FakeTravelerRepository
    private lateinit var settlements: FakeSettlementRepository
    private val store: MergeSuggestionStore = InMemoryMergeSuggestionStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trips = FakeTripRepository(listOf(ME to london))
        expenses = FakeExpenseRepository(trips, listOf(hotel, zara))
        travelers = FakeTravelerRepository(trips, expenses, listOf(yarden))
        settlements = FakeSettlementRepository(trips, listOf(paidBack))
        travelers.settlements = settlements
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm() =
        SplitViewModel(1, trips, expenses, travelers, settlements, auth, clock, dispatcher, members, store).also {
            it.uiState.launchIn(backgroundScope)
        }

    private fun SplitViewModel.loaded() = uiState.value as SplitUiState.Loaded

    @Test
    fun theTravelerWithHerName_isSuggestedOnce_andLabeledNameOnly() = runTest(dispatcher) {
        val state = vm().loaded()

        assertEquals(MergeSuggestion(yarden, her, "Yarden Tal Ronen", isYou = false, blocked = null), state.suggestion)
        assertEquals(listOf(ME, her, yarden.uuid), state.people.map { it.key })
        assertTrue((state.people.last() as Person.Named).nameOnly)
        assertFalse((state.person(her) as Person.Member).left)
    }

    @Test
    fun merge_movesHerExpensesAndPayments_removesTheTraveler_andTheDebtStaysTheSame() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(listOf(Transfer(yarden.uuid, ME, 1_500)), vm.loaded().transfers)

        vm.onMergeSuggestion()

        val state = vm.loaded()
        assertEquals(listOf(ME, her), state.people.map { it.key })
        assertNull(state.suggestion)
        assertTrue(travelers.travelers.value.isEmpty())
        assertEquals(listOf(listOf(ME, her), listOf(ME, her)), expenses.expenses.value.map { it.splitBetween })
        assertEquals(her, expenses.expenses.value.single { it.id == 2L }.paidBy)
        assertEquals(her, settlements.settlements.value.single().fromKey)
        // Who owes whom: the same amount, now from her account.
        assertEquals(listOf(Transfer(her, ME, 1_500)), state.transfers)
    }

    @Test
    fun keepSeparate_isNeverSuggestedAgain_butTheLabelStays() = runTest(dispatcher) {
        vm().onDismissSuggestion()

        val again = vm().loaded()

        assertNull(again.suggestion)
        assertEquals(listOf(yarden), travelers.travelers.value)
        assertTrue((again.people.last() as Person.Named).nameOnly)
    }

    @Test
    fun aDoubleTap_mergesOnce() = runTest(dispatcher) {
        val vm = vm()
        travelers.gate = CompletableDeferred()

        vm.onMergeSuggestion()
        vm.onMergeSuggestion()
        assertTrue(vm.loaded().isMerging)
        travelers.gate!!.complete(Unit)

        assertEquals(listOf(yarden.id to her), travelers.merges)
        assertFalse(vm.loaded().isMerging)
    }

    @Test
    fun anExpenseSharedByBothOfThem_saysWhy_andMergesNothing() = runTest(dispatcher) {
        expenses.expenses.value += Expense(3, 1, "Taxi", 9_000, ExpenseCategory.TRANSPORT, today, paidBy = ME, splitBetween = listOf(ME, her, yarden.uuid))
        val vm = vm()

        assertEquals(MergeBlock.SHARED_TOGETHER, vm.loaded().suggestion?.blocked)
        vm.onMergeSuggestion()

        assertTrue(travelers.merges.isEmpty())
        assertEquals(listOf(yarden), travelers.travelers.value)
    }
}
