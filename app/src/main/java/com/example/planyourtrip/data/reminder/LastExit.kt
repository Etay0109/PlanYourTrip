package com.example.planyourtrip.data.reminder

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.time.Instant
import java.time.ZoneId

/**
 * How the app's previous process ended, for the reminder logs (`adb logcat -s TripReminder`): a force-stop removes
 * every alarm and WorkManager job, and nothing can start the app again until it is opened, so a reminder due in
 * between comes only then (2026-10-14: Android Studio's Stop at 15:44, the 18:00 reminder came at 18:35).
 */
enum class ExitKind {
    /** "Force stop" in Settings, `adb shell am force-stop`, Android Studio's Stop or Run: alarms and jobs are gone. */
    FORCE_STOP,

    /** Swiped out of the recent apps: only the process ended, alarms and jobs are kept. */
    SWIPED_AWAY,

    /** Anything else (low memory, crash, update, `am kill`, …): alarms and jobs are kept. */
    OTHER,
}

data class LastExit(val kind: ExitKind, val at: Instant, val reason: Int, val description: String?)

/**
 * Pure. Android reports a force-stop, a swipe from the recent apps and `am kill` all as "user requested"; only the
 * description tells them apart, with or without a "[TAG] " prefix (Android 16 adds one): a force-stop is
 * "stop <package> due to …" (Samsung, Android 12) or "[FORCE STOP] stop …", a swipe "remove task…" (Samsung:
 * "remove task:recent-task-trimmed"), `am kill` "[KILL BACKGROUND] kill background". Unknown texts are [ExitKind.OTHER].
 */
fun exitKind(userRequested: Boolean, description: String?): ExitKind {
    if (!userRequested || description == null) return ExitKind.OTHER
    val text = description.trim().lowercase()
    val withoutTag = text.replace(Regex("""^\[[^\]]*]\s*"""), "")
    return when {
        text.startsWith("[force stop]") || withoutTag.startsWith("stop ") -> ExitKind.FORCE_STOP
        withoutTag.startsWith("remove task") -> ExitKind.SWIPED_AWAY
        else -> ExitKind.OTHER
    }
}

/** Pure: the log line about [exit] (null = unknown: Android 10 or older, or no earlier process). */
fun lastExitLine(exit: LastExit?, zone: ZoneId): String {
    if (exit == null) return "Last exit: unknown"
    val time = exit.at.atZone(zone).toLocalDateTime().withNano(0)
    val detail = exit.description?.let { " ($it)" }.orEmpty()
    return when (exit.kind) {
        ExitKind.FORCE_STOP ->
            "Last exit: FORCE-STOPPED at $time$detail: alarms and jobs were cleared, " +
                "nothing ran until the app was opened"
        ExitKind.SWIPED_AWAY -> "Last exit: removed from recent apps at $time (alarms kept)"
        ExitKind.OTHER -> "Last exit: reason ${exit.reason} at $time$detail"
    }
}

/**
 * The previous process's exit (Android 11+), else null. Only a process that was running leaves a record, so a
 * force-stop while the app wasn't running can't be seen.
 */
fun readLastExit(context: Context): LastExit? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val info = try {
        context.getSystemService(ActivityManager::class.java)
            ?.getHistoricalProcessExitReasons(context.packageName, 0, 1)
            ?.firstOrNull()
    } catch (_: RuntimeException) {
        null
    } ?: return null
    return LastExit(
        kind = exitKind(info.reason == ApplicationExitInfo.REASON_USER_REQUESTED, info.description),
        at = Instant.ofEpochMilli(info.timestamp),
        reason = info.reason,
        description = info.description,
    )
}
