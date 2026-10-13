package com.example.planyourtrip.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.planyourtrip.PlanYourTripApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A trip's reminder alarm went off ([AndroidReminderWork]): [ReminderDelivery] shows it if it is still wanted. */
class TripReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val tripUuid = intent.getStringExtra(EXTRA_TRIP_UUID) ?: return
        val uid = intent.getStringExtra(EXTRA_UID) ?: return
        val container = (context.applicationContext as PlanYourTripApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.reminderDelivery.deliver(tripUuid, uid)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.example.planyourtrip.TRIP_REMINDER_ALARM"
        const val EXTRA_TRIP_UUID = "tripUuid"
        const val EXTRA_UID = "uid"
    }
}
