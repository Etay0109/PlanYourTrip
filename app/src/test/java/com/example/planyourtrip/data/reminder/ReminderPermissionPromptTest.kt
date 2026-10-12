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
        val requests = mutableListOf<Unit>()
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
}
