package com.example.planyourtrip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.planyourtrip.data.reminder.TripReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Debug builds only: runs the real [TripReminderWorker] now (no 18:00 wait) for every trip of the signed-in account
 * that starts tomorrow, so the notification can be checked on an emulator:
 * `adb shell am broadcast -a com.example.planyourtrip.DEBUG_TRIP_REMINDER -p com.example.planyourtrip`
 */
class DebugReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PlanYourTripApplication).container
        val uid = container.authRepository.currentUser.value?.uid ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tomorrow = LocalDate.now(container.clock).plusDays(1)
                container.tripRepository.observeTrips(uid).first()
                    .filter { it.startDate == tomorrow }
                    .forEach { trip ->
                        val request = OneTimeWorkRequestBuilder<TripReminderWorker>()
                            .setInputData(TripReminderWorker.input(trip.uuid, uid))
                            .build()
                        WorkManager.getInstance(context).enqueue(request)
                    }
            } finally {
                pending.finish()
            }
        }
    }
}
