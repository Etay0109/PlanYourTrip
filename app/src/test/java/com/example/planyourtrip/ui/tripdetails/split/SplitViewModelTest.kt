package com.example.planyourtrip.ui.tripdetails.split

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeExpenseRepository
import com.example.planyourtrip.FakeSettlementRepository
import com.example.planyourtrip.FakeTravelerRepository
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ME
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.common.split.TravelerNameError
import com.example.planyourtrip.ui.common.split.Transfer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SplitViewModelTest {

    private val today = LocalDate.of(2026, 10, 21)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val dispatcher = UnconfinedTestDispatcher()
    private val auth = FakeAuthRepository(AuthUser("u1", "Etay", "etay@example.com"))

    private val rome = Trip(1, "Rome, Italy", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 24), TripColor.SKY)
    private val othersTrip = Trip(2, "Paris", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 3), TripColor.MINT)

    private val noa = Traveler(1, 1, "noa", "Noa", TravelerColor.ROSE)
    private val amit = Traveler(2, 1, "amit", "Amit", TravelerColor.INDIGO)
    private val dana = Traveler(3, 1, "dana", "Dana", TravelerColor.PURPLE)

    /** Paid by Noa for all three of us: I owe Noa 60, Amit owes Noa 60. */
    private val dinner = Expense(
        1, 1, "Dinner", 18_000, ExpenseCategory.FOOD, LocalDate.of(2026, 10, 20),
        paidBy = "noa", splitBetween = listOf(ME, "noa", "amit"),
    )

    /** Mine only: never shared. */
    private val sim = Expense(2, 1, "SIM", 5_000, ExpenseCategory.OTHER, LocalDate.of(2026, 10, 19))

    private lateinit var trips: FakeTripRepository
    private lateinit var expenses: FakeExpenseRepository
    private lateinit var travelers: FakeTravelerRepository
    private lateinit var settlements: FakeSettlementRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trips = FakeTripRepository(listOf("u1" to rome, "u2" to othersTrip))
        expenses = FakeExpenseRepository(trips, listOf(dinner, sim))
        travelers = FakeTravelerRepository(trips, expenses, listOf(noa, amit, dana))
        settlements = FakeSettlementRepository(trips)
        travelers.settlements = settlements
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(tripId: Long = 1) =
        SplitViewModel(tripId, trips, expenses, travelers, settlements, auth, clock, dispatcher).also {
            it.uiState.launchIn(backgroundScope)
        }

    private fun SplitViewModel.loaded() = uiState.value as SplitUiState.Loaded

    @Test
    fun loads_transfersYouFirst_andTheSharedCount() = runTest(dispatcher) {
        val state = vm().loaded()
        assertEquals(listOf(Transfer(ME, "noa", 6_000), Transfer("amit", "noa", 6_000)), state.transfers)
        assertEquals(1, state.sharedExpenseCount)
        assertEquals(listOf(ME, "noa", "amit", "dana"), state.people.map { it.key })
        assertTrue(state.hasOthers)
    }

    @Test
    fun onlyMe_hasNoOthersAndNoTransfers() = runTest(dispatcher) {
        travelers.travelers.value = emptyList()
        expenses.expenses.value = listOf(sim)
        val state = vm().loaded()
        assertFalse(state.hasOthers)
        assertTrue(state.transfers.isEmpty())
        assertEquals(0, state.sharedExpenseCount)
    }

    @Test
    fun anotherAccountsTrip_isNotFound() = runTest(dispatcher) {
        val vm = vm(tripId = 2)
        assertEquals(SplitUiState.NotFound, vm.uiState.value)
        vm.onAddTraveler()
        assertNull(vm.travelerDialog.value)
    }

    // ---- Travelers ----

    @Test
    fun addTraveler_savesTheTrimmedName() = runTest(dispatcher) {
        val vm = vm()
        vm.onAddTraveler()
        vm.onTravelerNameChange("  Yoni ")
        vm.onSaveTraveler()
        assertNull(vm.travelerDialog.value)
        assertEquals(listOf("Yoni"), travelers.added)
        assertEquals("Yoni", vm.loaded().travelers.last().name)
    }

    @Test
    fun addTraveler_blankDuplicateOrYou_isRejected() = runTest(dispatcher) {
        val vm = vm()
        vm.onAddTraveler()
        vm.onSaveTraveler()
        assertEquals(TravelerNameError.EMPTY, vm.travelerDialog.value?.error)
        vm.onTravelerNameChange("noa")
        assertNull(vm.travelerDialog.value?.error) // editing clears the error
        vm.onSaveTraveler()
        assertEquals(TravelerNameError.DUPLICATE, vm.travelerDialog.value?.error)
        vm.onTravelerNameChange("YOU")
        vm.onSaveTraveler()
        assertEquals(TravelerNameError.RESERVED, vm.travelerDialog.value?.error)
        assertTrue(travelers.added.isEmpty())
    }

    @Test
    fun addTraveler_doubleTap_addsOnce() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        travelers.gate = gate
        val vm = vm()
        vm.onAddTraveler()
        vm.onTravelerNameChange("Yoni")
        vm.onSaveTraveler()
        vm.onSaveTraveler()
        vm.onDismissTravelerDialog() // ignored while saving
        assertTrue(vm.travelerDialog.value!!.isSaving)
        gate.complete(Unit)
        assertEquals(listOf("Yoni"), travelers.added)
        assertNull(vm.travelerDialog.value)
    }

    @Test
    fun renameTraveler_keepsOwnNameAllowed() = runTest(dispatcher) {
        val vm = vm()
        vm.onTravelerClick(dana)
        vm.onTravelerNameChange("DANA R")
        vm.onSaveTraveler()
        assertEquals("DANA R", travelers.travelers.value.first { it.id == dana.id }.name)
        vm.onTravelerClick(noa)
        vm.onTravelerNameChange("Amit")
        vm.onSaveTraveler()
        assertEquals(TravelerNameError.DUPLICATE, vm.travelerDialog.value?.error)
    }

    @Test
    fun removeTraveler_whoPaid_isBlocked() = runTest(dispatcher) {
        val vm = vm()
        vm.onTravelerClick(noa)
        assertTrue(vm.travelerDialog.value!!.removeBlocked)
        vm.onRemoveTraveler()
        assertTrue(noa in travelers.travelers.value)
    }

    @Test
    fun removeTraveler_whoSharesAnExpense_isBlocked() = runTest(dispatcher) {
        val vm = vm()
        vm.onTravelerClick(amit)
        assertTrue(vm.travelerDialog.value!!.removeBlocked)
    }

    @Test
    fun removeTraveler_inASettlement_isBlocked() = runTest(dispatcher) {
        settlements.settlements.value = listOf(Settlement(1, 1, "s1", "dana", ME, 1_000, today))
        val vm = vm()
        vm.onTravelerClick(dana)
        assertTrue(vm.travelerDialog.value!!.removeBlocked)
    }

    @Test
    fun removeTraveler_unused_removesThem() = runTest(dispatcher) {
        val vm = vm()
        vm.onTravelerClick(dana)
        assertFalse(vm.travelerDialog.value!!.removeBlocked)
        vm.onRemoveTraveler()
        assertNull(vm.travelerDialog.value)
        assertFalse(vm.loaded().travelers.any { it.uuid == "dana" })
    }

    @Test
    fun removeTraveler_usedElsewhereMeanwhile_staysAndSaysWhy() = runTest(dispatcher) {
        val vm = vm()
        vm.onTravelerClick(dana)
        // Dana is added to an expense on another screen before "Remove" is tapped.
        expenses.expenses.value = expenses.expenses.value + sim.copy(id = 3, splitBetween = listOf(ME, "dana"))
        vm.onRemoveTraveler()
        assertTrue(vm.travelerDialog.value!!.removeBlocked)
        assertTrue(dana in travelers.travelers.value)
    }

    // ---- Settle up ----

    @Test
    fun settleUp_full_marksPaidAndClearsTheDebt() = runTest(dispatcher) {
        val vm = vm()
        vm.onSettleUp()
        assertNull(vm.settleSheet.value!!.selected) // two open debts: nothing picked yet
        vm.onSelectTransfer(Transfer(ME, "noa", 6_000))
        assertEquals("60", vm.settleSheet.value!!.amount)
        vm.onMarkAsPaid()
        assertNull(vm.settleSheet.value)
        assertEquals(listOf(Settlement(1, 1, "s1", ME, "noa", 6_000, today)), settlements.settlements.value)
        assertEquals(listOf(Transfer("amit", "noa", 6_000)), vm.loaded().transfers)
    }

    @Test
    fun settleUp_partial_leavesTheRestOpen() = runTest(dispatcher) {
        val vm = vm()
        vm.onSettleUp()
        vm.onSelectTransfer(Transfer(ME, "noa", 6_000))
        vm.onSettleAmountChange("25.50")
        vm.onMarkAsPaid()
        assertEquals(2_550L, settlements.settlements.value.single().amountAgorot)
        assertEquals(Transfer(ME, "noa", 3_450), vm.loaded().transfers.first())
    }

    @Test
    fun settleUp_singleDebt_isPickedAlready() = runTest(dispatcher) {
        expenses.expenses.value = listOf(dinner.copy(splitBetween = listOf(ME, "noa")))
        val vm = vm()
        vm.onSettleUp()
        assertEquals(Transfer(ME, "noa", 9_000), vm.settleSheet.value!!.selected)
        assertEquals("90", vm.settleSheet.value!!.amount)
    }

    @Test
    fun settleUp_invalidAmounts_areRejected() = runTest(dispatcher) {
        val vm = vm()
        vm.onSettleUp()
        vm.onSelectTransfer(Transfer(ME, "noa", 6_000))
        vm.onSettleAmountChange("")
        vm.onMarkAsPaid()
        assertEquals(SettleAmountError.EMPTY, vm.settleSheet.value!!.error)
        vm.onSettleAmountChange("0")
        vm.onMarkAsPaid()
        assertEquals(SettleAmountError.INVALID, vm.settleSheet.value!!.error)
        vm.onSettleAmountChange("60.01")
        vm.onMarkAsPaid()
        assertEquals(SettleAmountError.TOO_MUCH, vm.settleSheet.value!!.error)
        assertTrue(settlements.settlements.value.isEmpty())
    }

    @Test
    fun settleUp_doubleTap_savesOnce() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        settlements.gate = gate
        val vm = vm()
        vm.onSettleUp()
        vm.onSelectTransfer(Transfer(ME, "noa", 6_000))
        vm.onMarkAsPaid()
        vm.onMarkAsPaid()
        vm.onDismissSettle() // ignored while saving
        gate.complete(Unit)
        assertEquals(1, settlements.settlements.value.size)
        assertNull(vm.settleSheet.value)
    }

    @Test
    fun settlements_neverChangeTheExpenses() = runTest(dispatcher) {
        val before = expenses.expenses.value
        val vm = vm()
        vm.onSettleUp()
        vm.onSelectTransfer(Transfer(ME, "noa", 6_000))
        vm.onMarkAsPaid()
        assertEquals(before, expenses.expenses.value)
    }

    @Test
    fun history_deleteWithConfirmation_reopensTheDebt() = runTest(dispatcher) {
        val paid = Settlement(1, 1, "s1", ME, "noa", 6_000, today)
        settlements.settlements.value = listOf(paid)
        val vm = vm()
        assertEquals(listOf(Transfer("amit", "noa", 6_000)), vm.loaded().transfers)

        vm.onShowHistory()
        assertEquals(SettlePage.HISTORY, vm.settleSheet.value!!.page)
        vm.onDeleteSettlement(paid)
        assertEquals(paid, vm.settlementToDelete.value)
        vm.onDismissDeleteSettlement()
        assertEquals(listOf(paid), settlements.settlements.value) // cancelled: kept

        vm.onDeleteSettlement(paid)
        vm.onConfirmDeleteSettlement()
        assertNull(vm.settlementToDelete.value)
        assertTrue(settlements.settlements.value.isEmpty())
        assertEquals(Transfer(ME, "noa", 6_000), vm.loaded().transfers.first())
    }

    @Test
    fun everythingSettled_hasNoTransfers() = runTest(dispatcher) {
        settlements.settlements.value = listOf(
            Settlement(1, 1, "s1", ME, "noa", 6_000, today),
            Settlement(2, 1, "s2", "amit", "noa", 6_000, today),
        )
        val state = vm().loaded()
        assertTrue(state.transfers.isEmpty())
        assertEquals(2, state.settlements.size)
    }

    @Test
    fun validateSettleAmount_rules() {
        assertEquals(SettleAmountError.EMPTY, validateSettleAmount(" ", 100))
        assertEquals(SettleAmountError.INVALID, validateSettleAmount("abc", 100))
        assertEquals(SettleAmountError.INVALID, validateSettleAmount("0.00", 100))
        assertEquals(SettleAmountError.TOO_MUCH, validateSettleAmount("1.01", 100))
        assertNull(validateSettleAmount("1", 100))
        assertNull(validateSettleAmount("0,5", 100))
    }
}
