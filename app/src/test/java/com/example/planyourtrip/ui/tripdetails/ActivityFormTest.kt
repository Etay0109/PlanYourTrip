package com.example.planyourtrip.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import com.example.planyourtrip.FakeActivityRepository
import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.DeletedActivity
import com.example.planyourtrip.data.trip.DeletedActivityStore
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.data.trip.TripColor
import com.example.planyourtrip.ui.tripdetails.activity.ActivityFieldError
import com.example.planyourtrip.ui.tripdetails.activity.ActivityFormViewModel
import com.example.planyourtrip.ui.tripdetails.activity.ActivityValidator
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
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityFormTest {

    private val today = LocalDate.of(2026, 10, 5)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()
    private val auth = FakeAuthRepository(AuthUser("u1", "Yael", "yael@example.com"))

    private val rome = Trip(1, "Rome", LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 17), TripColor.SKY)
    private val colosseum = TripActivity(
        5, 1, LocalDate.of(2026, 11, 13), LocalTime.of(10, 0), "Colosseum", ActivityType.ACTIVITY,
        note = "Skip the line", location = "Piazza del Colosseo", estimatedCostAgorot = 9_050, booked = true,
    )
    private lateinit var trips: FakeTripRepository
    private lateinit var repo: FakeActivityRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trips = FakeTripRepository(listOf("u1" to rome))
        repo = FakeActivityRepository(trips, listOf(colosseum))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val deleted = DeletedActivityStore()

    private fun vm(activityId: Long? = null, initialDate: LocalDate? = null, tripId: Long = 1) =
        ActivityFormViewModel(tripId, activityId, initialDate, repo, trips, auth, clock, deleted)

    @Test
    fun validator_rules() {
        assertEquals(ActivityFieldError.EMPTY_TITLE, ActivityValidator.validateTitle("  "))
        assertNull(ActivityValidator.validateTitle("Pantheon"))
        assertNull(ActivityValidator.validateCost("")) // optional
        assertNull(ActivityValidator.validateCost("  "))
        assertNull(ActivityValidator.validateCost("25"))
        assertNull(ActivityValidator.validateCost("0.5"))
        assertNull(ActivityValidator.validateCost("12,5"))
        assertEquals(ActivityFieldError.INVALID_COST, ActivityValidator.validateCost("0"))
        assertEquals(ActivityFieldError.INVALID_COST, ActivityValidator.validateCost("-3"))
        assertEquals(ActivityFieldError.INVALID_COST, ActivityValidator.validateCost("abc"))
        assertEquals(ActivityFieldError.INVALID_COST, ActivityValidator.validateCost("1.234"))
    }

    @Test
    fun newActivity_onAChosenDay_loadsTheTripRange() = runTest(dispatcher) {
        val vm = vm(initialDate = LocalDate.of(2026, 11, 15))
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertFalse(s.isLoading)
        assertFalse(s.isEditing)
        assertEquals(LocalDate.of(2026, 11, 15), s.date)
        assertEquals(rome.startDate, s.tripStart)
        assertEquals(rome.endDate, s.tripEnd)
        assertEquals(ActivityType.ACTIVITY, s.type)
        assertNull(s.startTime)
    }

    @Test
    fun newActivity_withoutADay_usesTheDefaultDay() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        assertEquals(rome.startDate, vm.uiState.value.date) // before the trip: the first day
    }

    @Test
    fun save_invalid_showsErrors_andEditingClearsOnlyThatError() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onCostChange("0")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(ActivityFieldError.EMPTY_TITLE, vm.uiState.value.titleError)
        assertEquals(ActivityFieldError.INVALID_COST, vm.uiState.value.costError)
        assertEquals(listOf(colosseum), repo.activities.value)

        vm.onTitleChange("Pantheon")
        assertNull(vm.uiState.value.titleError)
        assertEquals(ActivityFieldError.INVALID_COST, vm.uiState.value.costError)
    }

    @Test
    fun save_new_storesAllFields_andBlankOptionalsAsNull() = runTest(dispatcher) {
        val vm = vm(initialDate = LocalDate.of(2026, 11, 14))
        advanceUntilIdle()
        vm.onTitleChange("  Lunch at Campo de' Fiori ")
        vm.onTypeChange(ActivityType.DINING)
        vm.onStartTimeChange(LocalTime.of(13, 0))
        vm.onCostChange("120.5")
        vm.onLocationChange("   ")
        vm.onNoteChange(" Pinsa ")
        vm.onBookedChange(true)
        vm.onSave()
        advanceUntilIdle()

        val saved = repo.activities.value.last()
        assertEquals(
            TripActivity(
                6, 1, LocalDate.of(2026, 11, 14), LocalTime.of(13, 0), "Lunch at Campo de' Fiori",
                ActivityType.DINING, note = "Pinsa", location = null, estimatedCostAgorot = 12_050, booked = true,
            ),
            saved,
        )
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun save_withoutCostOrTime_keepsThemEmpty() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onTitleChange("Walk")
        vm.onTypeChange(ActivityType.OTHER)
        vm.onSave()
        advanceUntilIdle()
        val saved = repo.activities.value.last()
        assertNull(saved.estimatedCostAgorot)
        assertNull(saved.startTime)
        assertNull(saved.note)
    }

    @Test
    fun doubleTap_savesOnce() = runTest(dispatcher) {
        val vm = vm()
        advanceUntilIdle()
        vm.onTitleChange("Pantheon")
        vm.onSave()
        vm.onSave()
        advanceUntilIdle()
        assertEquals(2, repo.activities.value.size)
    }

    @Test
    fun edit_prefillsOnce_andUpdates() = runTest(dispatcher) {
        val vm = vm(activityId = 5)
        advanceUntilIdle()
        val s = vm.uiState.value
        assertTrue(s.isEditing)
        assertEquals("Colosseum", s.title)
        assertEquals("90.50", s.cost)
        assertEquals(LocalTime.of(10, 0), s.startTime)
        assertEquals("Skip the line", s.note)
        assertEquals("Piazza del Colosseo", s.location)
        assertTrue(s.booked)

        vm.onStartTimeChange(null)
        vm.onCostChange("")
        vm.onSave()
        advanceUntilIdle()
        val updated = repo.activities.value.single()
        assertNull(updated.startTime)
        assertNull(updated.estimatedCostAgorot)
        assertEquals("Colosseum", updated.title)
    }

    @Test
    fun edit_keepsADateOutsideTheTrip() = runTest(dispatcher) {
        repo.activities.value = listOf(colosseum.copy(date = LocalDate.of(2026, 11, 1)))
        val vm = vm(activityId = 5)
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 1), vm.uiState.value.date)
        vm.onSave()
        advanceUntilIdle()
        assertEquals(LocalDate.of(2026, 11, 1), repo.activities.value.single().date)
    }

    @Test
    fun delete_removesTheActivity_andOffersUndo() = runTest(dispatcher) {
        val vm = vm(activityId = 5)
        advanceUntilIdle()
        vm.onDelete()
        advanceUntilIdle()
        assertTrue(repo.activities.value.isEmpty())
        assertTrue(vm.uiState.value.done)
        assertEquals(DeletedActivity(colosseum), deleted.last.value)
    }

    @Test
    fun someoneElsesTrip_closesWithoutSaving() = runTest(dispatcher) {
        trips.trips.value = listOf("other" to rome)
        val vm = vm()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.done)
        vm.onTitleChange("Sneaky")
        vm.onSave()
        advanceUntilIdle()
        assertEquals(listOf(colosseum), repo.activities.value)
    }

    @Test
    fun everyType_savesWithoutALocation_andTheLocationIsTrimmedFreeText() = runTest(dispatcher) {
        ActivityType.entries.forEach { type ->
            val vm = vm()
            advanceUntilIdle()
            vm.onTitleChange(type.name)
            vm.onTypeChange(type)
            vm.onSave()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.done)
        }
        val added = repo.activities.value.drop(1)
        assertEquals(ActivityType.entries.toList(), added.map { it.type })
        assertTrue(added.all { it.location == null })

        val vm = vm()
        advanceUntilIdle()
        vm.onTitleChange("Dinner")
        vm.onTypeChange(ActivityType.DINING)
        vm.onLocationChange(" Da Enzo al 29, Trastevere ")
        vm.onSave()
        advanceUntilIdle()
        assertEquals("Da Enzo al 29, Trastevere", repo.activities.value.last().location)
    }

    // ---------- Process death ----------

    @Test
    fun edit_draftComesBackAfterProcessDeath_overTheStoredActivity() = runTest(dispatcher) {
        val saved = SavedStateHandle()
        val before = ActivityFormViewModel(1, 5, null, repo, trips, auth, clock, deleted, savedState = saved)
        advanceUntilIdle()
        val type = ActivityType.entries.first { it != ActivityType.ACTIVITY }
        before.onTitleChange("Colosseum at night")
        before.onTypeChange(type)
        before.onBookedChange(false)
        before.onNoteChange("Bring water")

        val after = ActivityFormViewModel(1, 5, null, repo, trips, auth, clock, deleted, savedState = saved)
        advanceUntilIdle()
        val s = after.uiState.value
        assertEquals("Colosseum at night", s.title)
        assertEquals(type, s.type)
        assertFalse(s.booked)
        assertEquals("Bring water", s.note)
        assertEquals("Piazza del Colosseo", s.location)
    }
}
