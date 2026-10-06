package com.example.planyourtrip.ui.auth

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
        vm.onSubmit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(FieldError.EMPTY_EMAIL, state.emailError)
        assertEquals(FieldError.EMPTY_PASSWORD, state.passwordError)
        assertTrue(repo.calls.isEmpty())
    }

    @Test
    fun signIn_validInput_callsRepository() = runTest(dispatcher) {
        vm.onEmailChange(" user@example.com ")
        vm.onPasswordChange("short")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf("signIn:user@example.com"), repo.calls)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.isAuthenticated)
    }

    @Test
    fun invalidInput_doesNotAuthenticate() = runTest(dispatcher) {
        vm.onEmailChange("bad")
        vm.onSubmit()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isAuthenticated)
    }

    @Test
    fun signUp_invalidInput_showsAllErrors() = runTest(dispatcher) {
        vm.onModeToggle()
        vm.onEmailChange("bad-email")
        vm.onPasswordChange("1234567")
        vm.onConfirmChange("different")
        vm.onSubmit()
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
        vm.onNameChange("Dana Levi")
        vm.onEmailChange("dana@example.com")
        vm.onPasswordChange("12345678")
        vm.onConfirmChange("12345678")
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(listOf("signUp:Dana Levi:dana@example.com"), repo.calls)
    }

    @Test
    fun modeToggle_clearsErrorsAndKeepsEmail() {
        vm.onEmailChange("x")
        vm.onSubmit()
        assertEquals(FieldError.INVALID_EMAIL, vm.uiState.value.emailError)

        vm.onModeToggle()

        val state = vm.uiState.value
        assertEquals(AuthMode.SIGN_UP, state.mode)
        assertEquals("x", state.email)
        assertNull(state.emailError)
        assertNull(state.passwordError)
    }

    @Test
    fun editingField_clearsItsError() {
        vm.onSubmit()
        vm.onEmailChange("a")
        val state = vm.uiState.value
        assertNull(state.emailError)
        assertEquals(FieldError.EMPTY_PASSWORD, state.passwordError)
    }

    // ---- Backend errors and double taps ----

    private fun fillValidSignIn() {
        vm.onEmailChange("user@example.com")
        vm.onPasswordChange("password1")
    }

    private fun fillValidSignUp() {
        vm.onModeToggle()
        vm.onNameChange("Dana Levi")
        vm.onEmailChange("dana@example.com")
        vm.onPasswordChange("12345678")
        vm.onConfirmChange("12345678")
    }

    @Test
    fun signIn_wrongCredentials_showsErrorUnderPassword() = runTest(dispatcher) {
        repo.nextSignInError = AuthError.INVALID_CREDENTIALS
        fillValidSignIn()
        vm.onSubmit()
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
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(FieldError.EMAIL_IN_USE, vm.uiState.value.emailError)
        assertFalse(vm.uiState.value.isAuthenticated)
    }

    @Test
    fun signUp_weakPassword_showsErrorUnderPassword() = runTest(dispatcher) {
        repo.nextSignUpError = AuthError.WEAK_PASSWORD
        fillValidSignUp()
        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(FieldError.WEAK_PASSWORD, vm.uiState.value.passwordError)
    }

    @Test
    fun noConnection_tooManyRequests_unknown_showSnackbarMessage() = runTest(dispatcher) {
        listOf(AuthError.NO_CONNECTION, AuthError.TOO_MANY_REQUESTS, AuthError.UNKNOWN).forEach { error ->
            repo.nextSignInError = error
            fillValidSignIn()
            vm.onSubmit()
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
        vm.onSubmit()
        assertTrue(vm.uiState.value.isLoading)
        vm.onSubmit() // second tap before the first request finishes
        advanceUntilIdle()

        assertEquals(listOf("signIn:user@example.com"), repo.calls)
    }
}
