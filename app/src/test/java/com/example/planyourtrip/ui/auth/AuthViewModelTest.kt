package com.example.planyourtrip.ui.auth

import androidx.lifecycle.SavedStateHandle
import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.data.auth.AuthError
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

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: FakeAuthRepository
    private lateinit var vm: AuthViewModel

    /** What the form holds; it reaches the ViewModel only on submit. */
    private var input = AuthInput()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repo = FakeAuthRepository()
        vm = AuthViewModel(repo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun signIn_emptyFields_showsErrorsAndSkipsRepository() = runTest(dispatcher) {
        vm.onSubmit(input)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(FieldError.EMPTY_EMAIL, state.emailError)
        assertEquals(FieldError.EMPTY_PASSWORD, state.passwordError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun signIn_validInput_callsRepository() = runTest(dispatcher) {
        input = input.copy(email = " user@example.com ")
        input = input.copy(password = "short")
        vm.onSubmit(input)
        advanceUntilIdle()

        assertEquals(listOf("signIn:user@example.com"), repo.calls)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.isAuthenticated)
    }

    @Test
    fun invalidInput_doesNotAuthenticate() = runTest(dispatcher) {
        input = input.copy(email = "bad")
        vm.onSubmit(input)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
    }

    @Test
    fun signUp_invalidInput_showsAllErrors() = runTest(dispatcher) {
        vm.onModeToggle()
        input = input.copy(email = "bad-email")
        input = input.copy(password = "1234567")
        input = input.copy(confirmPassword = "different")
        vm.onSubmit(input)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(FieldError.EMPTY_NAME, state.nameError)
        assertEquals(FieldError.INVALID_EMAIL, state.emailError)
        assertEquals(FieldError.PASSWORD_TOO_SHORT, state.passwordError)
        assertEquals(FieldError.PASSWORDS_DONT_MATCH, state.confirmError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun signUp_validInput_callsRepository() = runTest(dispatcher) {
        vm.onModeToggle()
        input = input.copy(fullName = "Dana Levi")
        input = input.copy(email = "dana@example.com")
        input = input.copy(password = "12345678")
        input = input.copy(confirmPassword = "12345678")
        vm.onSubmit(input)
        advanceUntilIdle()

        assertEquals(listOf("signUp:Dana Levi:dana@example.com"), repo.calls)
    }

    @Test
    fun modeToggle_clearsErrors() {
        input = input.copy(email = "x")
        vm.onSubmit(input)
        assertEquals(FieldError.INVALID_EMAIL, vm.uiState.value.emailError)

        vm.onModeToggle()

        val state = vm.uiState.value
        assertEquals(AuthMode.SIGN_UP, state.mode)
        assertNull(state.emailError)
        assertNull(state.passwordError)
    }

    @Test
    fun editingField_clearsItsError() {
        vm.onSubmit(input)
        vm.onEmailEdited()
        val state = vm.uiState.value
        assertNull(state.emailError)
        assertEquals(FieldError.EMPTY_PASSWORD, state.passwordError)
    }

    // ---- Backend errors and double taps ----

    private fun fillValidSignIn() {
        input = input.copy(email = "user@example.com")
        input = input.copy(password = "password1")
    }

    private fun fillValidSignUp() {
        vm.onModeToggle()
        input = input.copy(fullName = "Dana Levi")
        input = input.copy(email = "dana@example.com")
        input = input.copy(password = "12345678")
        input = input.copy(confirmPassword = "12345678")
    }

    @Test
    fun signIn_wrongCredentials_showsErrorUnderPassword() = runTest(dispatcher) {
        repo.nextSignInError = AuthError.INVALID_CREDENTIALS
        fillValidSignIn()
        vm.onSubmit(input)
        advanceUntilIdle()

        val s = vm.uiState.value
        assertEquals(FieldError.WRONG_CREDENTIALS, s.passwordError)
        assertFalse(s.isAuthenticated)
        assertFalse(s.isLoading)
        assertNull(s.message)
    }

    @Test
    fun signUp_emailInUse_showsErrorUnderEmail() = runTest(dispatcher) {
        repo.nextSignUpError = AuthError.EMAIL_IN_USE
        fillValidSignUp()
        vm.onSubmit(input)
        advanceUntilIdle()

        assertEquals(FieldError.EMAIL_IN_USE, vm.uiState.value.emailError)
        assertFalse(vm.uiState.value.isAuthenticated)
    }

    @Test
    fun signUp_weakPassword_showsErrorUnderPassword() = runTest(dispatcher) {
        repo.nextSignUpError = AuthError.WEAK_PASSWORD
        fillValidSignUp()
        vm.onSubmit(input)
        advanceUntilIdle()

        assertEquals(FieldError.WEAK_PASSWORD, vm.uiState.value.passwordError)
    }

    @Test
    fun noConnection_tooManyRequests_unknown_showSnackbarMessage() = runTest(dispatcher) {
        listOf(AuthError.NO_CONNECTION, AuthError.TOO_MANY_REQUESTS, AuthError.UNKNOWN).forEach { error ->
            repo.nextSignInError = error
            fillValidSignIn()
            vm.onSubmit(input)
            advanceUntilIdle()

            val s = vm.uiState.value
            assertEquals(AuthMessage.Failure(error), s.message)
            assertNull(s.emailError)
            assertNull(s.passwordError)
            assertFalse(s.isLoading)
            vm.onMessageShown()
            assertNull(vm.uiState.value.message)
        }
    }

    @Test
    fun submit_isIgnoredWhileLoading() = runTest(dispatcher) {
        fillValidSignIn()
        vm.onSubmit(input)
        assertTrue(vm.uiState.value.isLoading)
        vm.onSubmit(input) // second tap before the first request finishes
        advanceUntilIdle()

        assertEquals(listOf("signIn:user@example.com"), repo.calls)
    }

    @Test
    fun signUp_emptyFields_everyFieldGetsItsMessage() = runTest(dispatcher) {
        vm.onModeToggle()
        vm.onSubmit(input)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(FieldError.EMPTY_NAME, state.nameError)
        assertEquals(FieldError.EMPTY_EMAIL, state.emailError)
        assertEquals(FieldError.EMPTY_PASSWORD, state.passwordError)
        assertEquals(FieldError.EMPTY_CONFIRM, state.confirmError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun signUp_passwordTypedButNotConfirmed_stillSaysTheyDontMatch() {
        vm.onModeToggle()
        input = input.copy(password = "12345678")
        vm.onSubmit(input)

        assertEquals(FieldError.PASSWORDS_DONT_MATCH, vm.uiState.value.confirmError)
        assertNull(vm.uiState.value.passwordError)
    }

    @Test
    fun failedSubmit_countsUp_validSubmitDoesNot() = runTest(dispatcher) {
        assertEquals(0, vm.uiState.value.failedSubmits)
        vm.onSubmit(input)
        assertEquals(1, vm.uiState.value.failedSubmits)
        vm.onSubmit(input) // same errors again: a new value, so focus moves again
        assertEquals(2, vm.uiState.value.failedSubmits)

        fillValidSignIn()
        vm.onSubmit(input)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.failedSubmits)
        assertTrue(vm.uiState.value.isAuthenticated)
    }

    @Test
    fun switchingBack_clearsErrors() {
        vm.onModeToggle() // the bottom link: Sign In -> Sign Up
        input = input.copy(email = "dana@example.com")
        vm.onSubmit(input)
        assertEquals(FieldError.EMPTY_NAME, vm.uiState.value.nameError)

        vm.onModeToggle() // "Sign in"
        val state = vm.uiState.value
        assertEquals(AuthMode.SIGN_IN, state.mode)
        assertNull(state.nameError)
        assertNull(state.passwordError)
        assertNull(state.confirmError)
    }

    @Test
    fun mode_survivesProcessDeath() {
        val saved = SavedStateHandle()
        AuthViewModel(repo, saved).onModeToggle()

        assertEquals(AuthMode.SIGN_UP, AuthViewModel(repo, saved).uiState.value.mode)
    }
}
