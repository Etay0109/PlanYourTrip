package com.example.planyourtrip.data.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The only AlarmManager and WorkManager code. Each trip's reminder is scheduled twice, for the same time:
 * - an alarm (`setAndAllowWhileIdle`, [TripReminderAlarmReceiver]): fires within minutes even while the phone sleeps
 *   (a job waits for the next maintenance window, which can be hours on Samsung). No exact-alarm permission: on time
 *   to the minute isn't needed. Lost on a restart or a force-stop: [TripReminderScheduler] arms it again at every app
 *   start ([ReminderRestoreReceiver] starts the app after a restart or an update).
 * - a unique `OneTimeWorkRequest` ("trip-reminder-<uuid>", replaced on every change), the backup: WorkManager keeps it
 *   across restarts and updates and runs it late rather than never.
 * Whichever fires second finds the reminder shown already ([ReminderDelivery]).
 */
class AndroidReminderWork(private val context: Context, private val clock: Clock) : ReminderWork {
    private val workManager = WorkManager.getInstance(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(tripUuid: String, uid: String, at: Instant) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), alarmIntent(context, tripUuid, uid))
        val delay = Duration.between(clock.instant(), at).coerceAtLeast(Duration.ZERO)
        val request = OneTimeWorkRequestBuilder<TripReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(TripReminderWorker.input(tripUuid, uid))
            .addTag(TAG)
            .addTag(TRIP_TAG_PREFIX + tripUuid)
            .build()
        workManager.enqueueUniqueWork(uniqueName(tripUuid), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(tripUuid: String) {
        armedAlarm(context, tripUuid)?.let { alarmManager.cancel(it); it.cancel() }
        workManager.cancelUniqueWork(uniqueName(tripUuid))
    }

    override fun cancelAll(tripUuids: Collection<String>) {
        tripUuids.forEach { uuid -> armedAlarm(context, uuid)?.let { alarmManager.cancel(it); it.cancel() } }
        workManager.cancelAllWorkByTag(TAG)
    }

    override suspend fun pendingUuids(): Set<String> =
        workManager.getWorkInfosByTagFlow(TAG).first()
            .filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED || it.state == WorkInfo.State.RUNNING }
            .mapNotNull { info -> info.tags.firstOrNull { it.startsWith(TRIP_TAG_PREFIX) }?.removePrefix(TRIP_TAG_PREFIX) }
            .toSet()

    companion object {
        /** Every reminder request has this tag. */
        const val TAG = "trip-reminder"
        private const val TRIP_TAG_PREFIX = "trip-reminder:"

        fun uniqueName(tripUuid: String) = "trip-reminder-$tripUuid"

        /** One alarm per trip: the trip's own data uri, so two trips never share a `PendingIntent`. */
        private fun intent(context: Context, tripUuid: String): Intent =
            Intent(context, TripReminderAlarmReceiver::class.java)
                .setAction(TripReminderAlarmReceiver.ACTION_FIRE)
                .setData(Uri.parse("planyourtrip://trip-reminder/" + Uri.encode(tripUuid)))
                .putExtra(TripReminderAlarmReceiver.EXTRA_TRIP_UUID, tripUuid)

        private fun alarmIntent(context: Context, tripUuid: String, uid: String): PendingIntent =
            PendingIntent.getBroadcast(
                context, 0, intent(context, tripUuid).putExtra(TripReminderAlarmReceiver.EXTRA_UID, uid),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        /** The trip's alarm when one is armed (debug command, cancelling), else null. */
        fun armedAlarm(context: Context, tripUuid: String): PendingIntent? =
            PendingIntent.getBroadcast(
                context, 0, intent(context, tripUuid), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            )
    }
}
