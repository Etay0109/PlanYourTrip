package com.example.planyourtrip.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LastExitTest {
    private val zone = ZoneId.of("Asia/Jerusalem")

    @Test
    fun forceStop_fromAdbOrAndroidStudio() {
        // The exact description on the Samsung (2026-10-14, 15:44:28).
        assertEquals(ExitKind.FORCE_STOP, exitKind(true, "stop com.example.planyourtrip due to from pid 29014"))
        // Android 16 (emulator, 2026-10-14).
        assertEquals(
            ExitKind.FORCE_STOP,
            exitKind(true, "[FORCE STOP] stop com.example.planyourtrip due to from pid 5769"),
        )
    }

    @Test
    fun swipedOutOfRecents_isNotAForceStop() {
        assertEquals(ExitKind.SWIPED_AWAY, exitKind(true, "remove task:recent-task-trimmed"))
        assertEquals(ExitKind.SWIPED_AWAY, exitKind(true, "remove task"))
        assertEquals(ExitKind.SWIPED_AWAY, exitKind(true, "[REMOVE TASK] remove task"))
    }

    @Test
    fun otherReasons() {
        assertEquals(ExitKind.OTHER, exitKind(false, "stop com.example.planyourtrip"))
        assertEquals(ExitKind.OTHER, exitKind(false, null))
        // `adb shell am kill` on Android 16: alarms are kept.
        assertEquals(ExitKind.OTHER, exitKind(true, "[KILL BACKGROUND] kill background"))
        assertEquals(ExitKind.OTHER, exitKind(true, null))
    }

    @Test
    fun logLines() {
        val at = LocalDateTime.of(2026, 10, 14, 15, 44, 28, 639_000_000).atZone(zone).toInstant()
        val forced = lastExitLine(LastExit(ExitKind.FORCE_STOP, at, 10, "stop x due to from pid 1"), zone)
        assertTrue(forced, forced.startsWith("Last exit: FORCE-STOPPED at 2026-10-14T15:44:28 (stop x due to from pid 1)"))
        assertTrue(forced, "alarms and jobs were cleared" in forced)
        assertEquals(
            "Last exit: removed from recent apps at 2026-10-14T15:44:28 (alarms kept)",
            lastExitLine(LastExit(ExitKind.SWIPED_AWAY, at, 10, "remove task"), zone),
        )
        assertEquals("Last exit: reason 3 at 2026-10-14T15:44:28", lastExitLine(LastExit(ExitKind.OTHER, at, 3, null), zone))
        assertEquals("Last exit: unknown", lastExitLine(null, zone))
    }
}
