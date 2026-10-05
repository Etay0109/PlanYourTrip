package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeExpenseRepository
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseFieldError
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseFormViewModel
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
class ExpenseFormTest {

    private val today = LocalDate.of(2026, 10, 5)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()
    private val auth = FakeAuthRepository(AuthUser("u1", "Etay", "etay@example.com"))

    private val rome = Trip(1, "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY)
    private val dinner = Expense(5, 1, "Dinner", 12_050, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13))
    private lateinit var repo: FakeExpenseRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeExpenseRepository(FakeTripRepository(listOf("u1" to rome)), listOf(dinner))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(expenseId: Long? = null) = ExpenseFormViewModel(1, expenseId, repo, auth, clock)

    @Test
    fun validator_rules() {
        assertEquals(ExpenseFieldError.EMPTY_NAME, ExpenseValidator.validateName(" "))
        assertNull(ExpenseValidator.validateName("Taxi"))
        assertEquals(ExpenseFieldError.EMPTY_AMOUNT, ExpenseValidator.validateAmount(""))
        assertEquals(ExpenseFieldError.INVALID_AMOUNT, ExpenseValidator.validateAmount("0"))
        assertEquals(ExpenseFieldError.INVALID_AMOUNT, ExpenseValidator.validateAmount("0.00"))
        assertEquals(ExpenseFieldError.INVALID_AMOUNT, ExpenseValidator.validateAmount("-5"))
        assertEquals(ExpenseFieldError.INVALID_AMOUNT, ExpenseValidator.validateAmount("1.234"))
        assertNull(ExpenseValidator.validateAmount("0.01"))
        assertNull(ExpenseValidator.validateAmount("12,5"))
    }

    @Test
    fun newExpense_defaultsToTodayAndOther() {
        val s = vm().uiState.value
        assertFalse(s.isEditing)
        assertEquals(today, s.date)
        assertEquals(ExpenseCategory.OTHER, s.category)
    }

    @Test
    fun save_invalid_showsErrors_andEditingClearsOnlyThatError() = runTest(dispatcher) {
        val vm = vm()
        vm.onAmountChange("0")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(ExpenseFieldError.EMPTY_NAME, vm.uiState.value.nameError)
        assertEquals(ExpenseFieldError.INVALID_AMOUNT, vm.uiState.value.amountError)
        assertEquals(listOf(dinner), repo.expenses.value)

        vm.onNameChange("Taxi")
        assertNull(vm.uiState.value.nameError)
        assertEquals(ExpenseFieldError.INVALID_AMOUNT, vm.uiState.value.amountError)
    }

    @Test
    fun save_valid_addsExpenseInAgorot() = runTest(dispatcher) {
        val vm = vm()
        vm.onNameChange("  Taxi to hotel ")
        vm.onAmountChange("85.5")
        vm.onCategoryChange(ExpenseCategory.TRANSPORT)
        vm.onDateChange(LocalDate.of(2026, 11, 12))
        vm.onSave()
        vm.onSave() // double tap
        advanceUntilIdle()

        val added = repo.expenses.value.single { it.id != dinner.id }
        assertEquals(Expense(added.id, 1, "Taxi to hotel", 8_550, ExpenseCategory.TRANSPORT, LocalDate.of(2026, 11, 12)), added)
        assertEquals(2, repo.expenses.value.size)
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun edit_prefillsAndUpdates() = runTest(dispatcher) {
        val vm = vm(expenseId = 5)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s.isEditing)
        assertEquals("Dinner", s.name)
        assertEquals("120.50", s.amount)
        assertEquals(ExpenseCategory.FOOD, s.category)
        assertEquals(dinner.date, s.date)

        vm.onAmountChange("130")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf(dinner.copy(amountAgorot = 13_000)), repo.expenses.value)
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun delete_removesExpense() = runTest(dispatcher) {
        val vm = vm(expenseId = 5)
        advanceUntilIdle()
        vm.onDelete()
        advanceUntilIdle()
        assertTrue(repo.expenses.value.isEmpty())
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun otherAccount_cannotEditOrDelete() = runTest(dispatcher) {
        val stranger = ExpenseFormViewModel(1, 5, repo, FakeAuthRepository(AuthUser("u2", null, "x@y.z")), clock)
        advanceUntilIdle()
        assertEquals("", stranger.uiState.value.name)
        stranger.onDelete()
        advanceUntilIdle()
        assertEquals(listOf(dinner), repo.expenses.value)
    }
}
