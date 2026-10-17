package com.example.planyourtrip.data.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.planyourtrip.PlanYourTripApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * After a phone restart or an app update: starting the app is the point (alarms are gone; [TripReminderScheduler],
 * started in the Application, arms them again and shows a reminder missed this evening). Waits for that first pass
 * so the process isn't stopped halfway. When exact alarms were just allowed ("Alarms & reminders", Android 12+) the
 * app may already be running, so every alarm is armed again as an exact one ([TripReminderScheduler.armAgain]).
 * System broadcasts only (not exported).
 */
class ReminderRestoreReceiver : BroadcastReceiver() {
    // The Android 12 action is only a string compared here; older versions simply never send it.
    @SuppressLint("InlinedApi")
    override fun onReceive(context: Context, intent: Intent) {
        val exactAllowed = intent.action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            !exactAllowed
        ) return
        val container = (context.applicationContext as PlanYourTripApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(WAIT_MILLIS) {
                    container.tripReminderScheduler.awaitFirstPass()
                    if (exactAllowed) container.tripReminderScheduler.armAgain()
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        /** A broadcast receiver has about 10 seconds. */
        const val WAIT_MILLIS = 8_000L
    }
}
