package com.example.planyourtrip.data.reminder

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
 * so the process isn't stopped halfway. System broadcasts only (not exported).
 */
class ReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val container = (context.applicationContext as PlanYourTripApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(WAIT_MILLIS) { container.tripReminderScheduler.awaitFirstPass() }
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
