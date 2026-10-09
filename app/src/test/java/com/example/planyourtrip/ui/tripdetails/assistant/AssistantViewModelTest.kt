package com.example.planyourtrip.ui.tripdetails.assistant

import com.example.planyourtrip.FakeActivityRepository
import com.example.planyourtrip.FakeAssistantRepository
import com.example.planyourtrip.FakeAuthRepository
import com.example.planyourtrip.FakeExpenseRepository
import com.example.planyourtrip.FakeTravelerRepository
import com.example.planyourtrip.FakeTripRepository
import com.example.planyourtrip.ME
import com.example.planyourtrip.data.assistant.AssistantError
import com.example.planyourtrip.data.assistant.AssistantModel
import com.example.planyourtrip.data.assistant.AssistantResponse
import com.example.planyourtrip.data.assistant.ChatTurn
import com.example.planyourtrip.data.auth.AuthUser
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ExpenseInput
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.data.trip.TripColor
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
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

@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val today = LocalDate.of(2026, 10, 5)
    private val clock = Clock.fixed(today.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
    private val dispatcher = UnconfinedTestDispatcher()

    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private val rome = Trip(1, "Rome, Italy", nov(12), nov(17), TripColor.SKY, budgetAgorot = 600_000, currencyCode = "EUR")
    private val auth = FakeAuthRepository(AuthUser("u1", "Etay", "etay@example.com"))
    private lateinit var trips: FakeTripRepository
    private lateinit var expenses: FakeExpenseRepository
    private lateinit var activities: FakeActivityRepository

    private val planDay2 = AssistantResponse.Answer(
        """
        {"reply":"Here is day 2.","plan":{"date":"2026-11-13","title":"Vatican day","activities":[
          {"time":"09:30","title":"Vatican Museums","type":"ACTIVITY","note":"Book ahead","place":"Vatican Museums","costShekels":90},
          {"time":"13:30","title":"Lunch at Borgo Pio","type":"DINING","note":"Cacio e pepe"},
          {"time":"16:00","title":"Castel Sant'Angelo","type":"ACTIVITY","note":"Sunset","place":"Castel Sant'Angelo"}
        ]}}
        """.trimIndent(),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        trips = FakeTripRepository(listOf("u1" to rome))
        expenses = FakeExpenseRepository(trips)
        activities = FakeActivityRepository(
            trips,
            listOf(TripActivity(1, 1, nov(12), LocalTime.of(10, 0), "Colosseum", ActivityType.ACTIVITY)),
        )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(assistant: FakeAssistantRepository) =
        AssistantViewModel(1, trips, expenses, activities, auth, assistant, clock, FakeTravelerRepository(trips)).also {
            it.uiState.launchIn(backgroundScope)
        }

    @Test
    fun chips_comeFromTheTrip() = runTest(dispatcher) {
        val state = vm(FakeAssistantRepository()).uiState.value
        assertTrue(state.isReady)
        assertEquals(
            listOf(AssistantChip.PlanDay(2), AssistantChip.DinnerIdeas(1), AssistantChip.BudgetCheck),
            state.chips,
        )
    }

    @Test
    fun send_includesTheCurrentTripContext() = runTest(dispatcher) {
        expenses.addExpense(1, "u1", ExpenseInput("Flights", 250_000, ExpenseCategory.FLIGHTS, nov(1), paidBy = ME, splitBetween = listOf(ME)))
        val assistant = FakeAssistantRepository(AssistantResponse.Answer("""{"reply":"Yes, ₪3,500 left."}"""))
        val vm = vm(assistant)

        assertTrue(vm.onSend("  Am I within budget?  "))

        val request = assistant.requests.single()
        assertEquals("Am I within budget?", request.message)
        assertTrue(request.history.isEmpty())
        assertTrue("Destination: Rome, Italy" in request.tripContext)
        assertTrue("Trip currency: EUR" in request.tripContext)
        assertTrue("Budget: total ₪6,000, spent ₪2,500, remaining ₪3,500." in request.tripContext)
        assertTrue("10:00 Colosseum [ACTIVITY]" in request.tripContext)
        val messages = vm.uiState.value.messages
        assertEquals(ChatMessage.User(1, "Am I within budget?"), messages[0])
        assertEquals("Yes, ₪3,500 left.", (messages[1] as ChatMessage.Assistant).text)
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun send_budgetLineCountsOnlyMyShare() = runTest(dispatcher) {
        // ₪2,500 split with Noa: ₪1,250 is mine, like on the Budget tab.
        expenses.addExpense(
            1, "u1",
            ExpenseInput(
                "Flights", 250_000, ExpenseCategory.FLIGHTS, nov(1),
                paidBy = "noa", splitBetween = listOf(ME, "noa"),
            ),
        )
        val travelers = FakeTravelerRepository(trips, initial = listOf(Traveler(1, 1, "noa", "Noa", TravelerColor.ROSE)))
        val assistant = FakeAssistantRepository(AssistantResponse.Answer("""{"reply":"Yes."}"""))
        val vm = AssistantViewModel(1, trips, expenses, activities, auth, assistant, clock, travelers).also {
            it.uiState.launchIn(backgroundScope)
        }

        assertTrue(vm.onSend("Am I within budget?"))

        assertTrue("Budget: total ₪6,000, spent ₪1,250, remaining ₪4,750." in assistant.requests.single().tripContext)
    }

    @Test
    fun secondRequest_sendsTheEarlierTurnsAsHistory() = runTest(dispatcher) {
        val first = AssistantResponse.Answer("""{"reply":"Hi"}""")
        val assistant = FakeAssistantRepository(first, AssistantResponse.Answer("""{"reply":"Sure"}"""))
        val vm = vm(assistant)
        vm.onSend("Hello")
        vm.onSend("Plan day 2")
        assertEquals(listOf(ChatTurn(true, "Hello"), ChatTurn(false, first.json)), assistant.requests[1].history)
    }

    @Test
    fun whileARequestRuns_typingIsShown_andSendingIsBlocked() = runTest(dispatcher) {
        val assistant = FakeAssistantRepository(AssistantResponse.Answer("""{"reply":"Hi"}"""))
        val gate = CompletableDeferred<Unit>()
        assistant.gate = gate
        val vm = vm(assistant)

        assertTrue(vm.onSend("Hello"))
        assertTrue(vm.uiState.value.isSending)
        assertFalse(vm.onSend("Hello again"))

        gate.complete(Unit)
        assertFalse(vm.uiState.value.isSending)
        assertEquals(1, assistant.requests.size)
        assertEquals(2, vm.uiState.value.messages.size)
    }

    @Test
    fun blankMessage_isNotSent() = runTest(dispatcher) {
        val assistant = FakeAssistantRepository()
        val vm = vm(assistant)
        assertFalse(vm.onSend("   "))
        assertTrue(assistant.requests.isEmpty())
        assertTrue(vm.uiState.value.messages.isEmpty())
    }

    @Test
    fun noTrip_cannotSend() = runTest(dispatcher) {
        val assistant = FakeAssistantRepository()
        val vm = AssistantViewModel(99, trips, expenses, activities, auth, assistant, clock, FakeTravelerRepository(trips))
        assertFalse(vm.onSend("Hello"))
        assertTrue(assistant.requests.isEmpty())
    }

    @Test
    fun eachError_isShown_andTryAgainResendsTheSameMessageOnce() = runTest(dispatcher) {
        // Errors that another model would not fix: shown right away, without switching models.
        for (error in listOf(AssistantError.NO_CONNECTION, AssistantError.APP_CHECK, AssistantError.UNKNOWN)) {
            val assistant = FakeAssistantRepository(
                AssistantResponse.Failed(error),
                AssistantResponse.Answer("""{"reply":"Back online"}"""),
            )
            val vm = vm(assistant)
            vm.onSend("Plan day 2")
            assertEquals(error, vm.uiState.value.error)
            assertEquals(1, assistant.requests.size)
            assertEquals(1, vm.uiState.value.messages.size)

            vm.onRetry()
            assertNull(vm.uiState.value.error)
            assertEquals(listOf("Plan day 2", "Plan day 2"), assistant.requests.map { it.message })
            // The failed attempt is not history, and the user bubble is not repeated.
            assertTrue(assistant.requests[1].history.isEmpty())
            val messages = vm.uiState.value.messages
            assertEquals(2, messages.size)
            assertEquals("Back online", (messages[1] as ChatMessage.Assistant).text)
        }
    }

    @Test
    fun busyOrRateLimit_triesTheOtherModelOnce_andThePlanArrives() = runTest(dispatcher) {
        // The emulator case: "Plan day 1 for me" got a 503 "high demand", then the free quota ran out.
        for (error in listOf(AssistantError.BUSY, AssistantError.RATE_LIMIT)) {
            val assistant = FakeAssistantRepository(AssistantResponse.Failed(error), planDay2)
            val vm = vm(assistant)

            vm.onSend("Plan day 2 for me")

            assertEquals(listOf(AssistantModel.PRIMARY, AssistantModel.FALLBACK), assistant.requests.map { it.model })
            // Same message, context and history: only the model changes.
            assertEquals(assistant.requests[0], assistant.requests[1].copy(model = AssistantModel.PRIMARY))
            val answer = vm.uiState.value.messages[1] as ChatMessage.Assistant
            assertEquals(3, answer.plan!!.plan.activities.size)
            assertNull(vm.uiState.value.error)
            assertFalse(vm.uiState.value.isSending)
        }
    }

    @Test
    fun bothModelsFail_showsTheLastError_andTryAgainStartsWithTheDefaultModel() = runTest(dispatcher) {
        val assistant = FakeAssistantRepository(
            AssistantResponse.Failed(AssistantError.BUSY),
            AssistantResponse.Failed(AssistantError.RATE_LIMIT),
            AssistantResponse.Answer("""{"reply":"Here you go"}"""),
        )
        val vm = vm(assistant)
        vm.onSend("Plan day 2 for me")
        assertEquals(AssistantError.RATE_LIMIT, vm.uiState.value.error)
        assertEquals(2, assistant.requests.size)

        vm.onRetry()
        assertEquals(AssistantModel.PRIMARY, assistant.requests[2].model)
        assertNull(vm.uiState.value.error)
        assertEquals("Here you go", (vm.uiState.value.messages[1] as ChatMessage.Assistant).text)
    }

    @Test
    fun theOtherModel_ofEachModel() {
        assertEquals(AssistantModel.FALLBACK, AssistantModel.PRIMARY.other)
        assertEquals(AssistantModel.PRIMARY, AssistantModel.FALLBACK.other)
        assertEquals("gemini-3.5-flash-lite", AssistantModel.PRIMARY.modelName)
        assertEquals("gemini-3.8-flash", AssistantModel.FALLBACK.modelName)
    }

    @Test
    fun unreadableAnswer_isAnError() = runTest(dispatcher) {
        val assistant = FakeAssistantRepository(AssistantResponse.Answer("Sure! Day 2: museums."))
        val vm = vm(assistant)
        vm.onSend("Plan day 2")
        assertEquals(AssistantError.UNREADABLE, vm.uiState.value.error)
    }

    @Test
    fun retryWithoutError_doesNothing() = runTest(dispatcher) {
        val assistant = FakeAssistantRepository(AssistantResponse.Answer("""{"reply":"Hi"}"""))
        val vm = vm(assistant)
        vm.onSend("Hello")
        vm.onRetry()
        assertEquals(1, assistant.requests.size)
    }

    @Test
    fun addPlan_addsOnlyTheCheckedSuggestions_andTurnsIntoAdded() = runTest(dispatcher) {
        val vm = vm(FakeAssistantRepository(planDay2))
        vm.onSend("Plan day 2 for me")
        val message = vm.uiState.value.messages[1] as ChatMessage.Assistant
        val card = message.plan!!
        assertEquals(2, card.dayNumber)
        assertTrue(card.withinTrip)
        assertEquals(setOf(0, 1, 2), card.checked)

        vm.onToggleSuggestion(message.id, 1)
        vm.onAddPlan(message.id)

        val added = activities.activities.value.filter { it.date == nov(13) }
        assertEquals(listOf("Vatican Museums", "Castel Sant'Angelo"), added.map { it.title })
        assertEquals(9_000L, added[0].estimatedCostAgorot)
        assertEquals(LocalTime.of(9, 30), added[0].startTime)
        assertFalse(added.any { it.booked })
        // The venues the assistant named become the location text (nothing is looked up).
        assertEquals(listOf("Vatican Museums", "Castel Sant'Angelo"), added.map { it.location })
        val after = (vm.uiState.value.messages[1] as ChatMessage.Assistant).plan!!
        assertEquals(AddStatus.ADDED, after.status)
        assertFalse(after.canAdd)

        // A second tap and toggles after adding change nothing.
        vm.onAddPlan(message.id)
        vm.onToggleSuggestion(message.id, 0)
        assertEquals(3, activities.activities.value.size)
        assertEquals(setOf(0, 2), (vm.uiState.value.messages[1] as ChatMessage.Assistant).plan!!.checked)
    }

    @Test
    fun planOutsideTheTrip_cannotBeAdded() = runTest(dispatcher) {
        val outside = AssistantResponse.Answer(planDay2.json.replace("2026-11-13", "2026-11-20"))
        val vm = vm(FakeAssistantRepository(outside))
        vm.onSend("Plan day 9")
        val message = vm.uiState.value.messages[1] as ChatMessage.Assistant
        assertFalse(message.plan!!.withinTrip)

        vm.onAddPlan(message.id)
        assertEquals(1, activities.activities.value.size)
        assertEquals(AddStatus.NEW, (vm.uiState.value.messages[1] as ChatMessage.Assistant).plan!!.status)
    }

    @Test
    fun editedTripDates_updateThePlanCard() = runTest(dispatcher) {
        val vm = vm(FakeAssistantRepository(planDay2))
        vm.onSend("Plan day 2 for me")
        trips.updateTrip(1, "u1", "Rome, Italy", nov(14), nov(18), "EUR")
        val card = (vm.uiState.value.messages[1] as ChatMessage.Assistant).plan!!
        assertFalse(card.withinTrip)
    }
}
