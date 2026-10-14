package com.example.planyourtrip

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import com.example.planyourtrip.data.reminder.AndroidReminderWork
import com.example.planyourtrip.data.reminder.REMINDER_LOG_TAG
import com.example.planyourtrip.data.reminder.canUseExactAlarms
import com.example.planyourtrip.data.reminder.isDue
import com.example.planyourtrip.data.reminder.lastExitLine
import com.example.planyourtrip.data.reminder.readLastExit
import com.example.planyourtrip.data.reminder.shownKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Debug builds only, sent with adb (see CLAUDE.md "Commands"):
 * - `DEBUG_TRIP_REMINDER`: shows the reminder now (no 18:00 wait) for every trip of the signed-in account that starts
 *   tomorrow, through the real [com.example.planyourtrip.data.reminder.ReminderDelivery], so it then counts as shown;
 *   `--ez force true` shows it again even when it was shown already.
 * - `DEBUG_NEXT_REMINDER`: logs every scheduled reminder, soonest first (`adb logcat -s TripReminder`): trip uuid,
 *   local time, owed / shown / due, the WorkManager backup's state and whether the alarm is armed; before that whether
 *   alarms are exact and how the previous process ended (a force-stop clears every alarm and job).
 */
class DebugReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PlanYourTripApplication).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(container, intent.getBooleanExtra("force", false))
                    ACTION_NEXT -> logNext(context, container)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(container: AppContainer, force: Boolean) {
        val uid = container.authRepository.currentUser.value?.uid ?: return
        val tomorrow = LocalDate.now(container.clock).plusDays(1)
        container.tripRepository.observeTrips(uid).first()
            .filter { it.startDate == tomorrow }
            .forEach { trip -> container.reminderDelivery.deliver(trip.uuid, uid, force) }
    }

    private suspend fun logNext(context: Context, container: AppContainer) {
        val settings = container.reminderSettings
        val shown = settings.shown()
        val now = container.clock.instant()
        val uid = container.authRepository.currentUser.value?.uid
        val trips = uid?.let { container.tripRepository.observeTrips(it).first() }.orEmpty().associateBy { it.uuid }
        val scheduled = settings.scheduled().entries.sortedBy { it.value.at }
        val exact = context.getSystemService(AlarmManager::class.java).canUseExactAlarms()
        Log.i(
            REMINDER_LOG_TAG,
            "Switch ${if (settings.enabled.value) "on" else "off"}, ${scheduled.size} scheduled, " +
                "alarms ${if (exact) "exact" else "inexact (up to 1 h late)"}",
        )
        Log.i(REMINDER_LOG_TAG, lastExitLine(readLastExit(context), container.clock.zone))
        scheduled.forEach { (uuid, reminder) ->
            val trip = trips[uuid]
            val isShown = trip != null && shownKey(uuid, reminder.uid, trip.startDate) in shown
            val work = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(AndroidReminderWork.uniqueName(uuid)).first()
                .joinToString { it.state.name }.ifEmpty { "none" }
            val alarm = if (AndroidReminderWork.armedAlarm(context, uuid) != null) "armed" else "not armed"
            val time = reminder.at.atZone(container.clock.zone).toLocalDateTime()
            val state = when {
                isShown -> "shown"
                reminder.isDue(now) -> "due"
                else -> "waiting"
            }
            Log.i(REMINDER_LOG_TAG, "$uuid at $time: $state, work $work, alarm $alarm")
        }
    }

    private companion object {
        const val ACTION_FIRE = "com.example.planyourtrip.DEBUG_TRIP_REMINDER"
        const val ACTION_NEXT = "com.example.planyourtrip.DEBUG_NEXT_REMINDER"
    }
}
