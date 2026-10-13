package com.example.planyourtrip.data.reminder

import com.example.planyourtrip.FakeReminderSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderPermissionPromptTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val clock = Clock.fixed(LocalDate.of(2026, 10, 12).atTime(10, 0).atZone(zone).toInstant(), zone)
    private val settings = FakeReminderSettings()
    private val prompt = ReminderPermissionPrompt(settings, clock)

    @Test
    fun onlyAFutureTrip_asks_andOnlyUntilAsked() = runTest {
        settings.batteryTipShown = true // only the permission here
        val requests = mutableListOf<ReminderSetupRequest>()
        backgroundScope.launch { prompt.requests.toList(requests) }
        runCurrent()

        prompt.onTripAdded(LocalDate.of(2026, 10, 12)) // starts today
        prompt.onTripAdded(LocalDate.of(2026, 9, 1)) // past trip
        runCurrent()
        assertEquals(0, requests.size)

        prompt.onTripAdded(LocalDate.of(2026, 10, 13))
        runCurrent()
        assertEquals(1, requests.size)

        prompt.onAsked()
        prompt.onTripAdded(LocalDate.of(2026, 12, 1))
        runCurrent()
        assertEquals(1, requests.size)
    }

    @Test
    fun afterThePermission_theBatteryScreenIsOfferedUntilShown_alsoWhenRemindersAreTurnedOn() = runTest {
        val requests = mutableListOf<ReminderSetupRequest>()
        backgroundScope.launch { prompt.requests.toList(requests) }
        settings.permissionAsked = true
        prompt.onTripAdded(LocalDate.of(2026, 10, 13))
        runCurrent()
        assertEquals(listOf(ReminderSetupRequest(askPermission = false)), requests)

        assertEquals(true, prompt.wantsBatteryTip("samsung", exempt = false))
        assertEquals(false, prompt.wantsBatteryTip("Google", exempt = false))
        prompt.onBatteryTipShown()
        assertEquals(false, prompt.wantsBatteryTip("samsung", exempt = false))
        prompt.onTripAdded(LocalDate.of(2026, 12, 1))
        prompt.onRemindersTurnedOn()
        runCurrent()
        assertEquals(1, requests.size)
    }

    @Test
    fun turningRemindersOn_offersTheBatteryScreen() = runTest {
        val requests = mutableListOf<ReminderSetupRequest>()
        backgroundScope.launch { prompt.requests.toList(requests) }
        prompt.onRemindersTurnedOn()
        runCurrent()
        assertEquals(listOf(ReminderSetupRequest(askPermission = false)), requests)
    }
}
