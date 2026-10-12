package com.example.planyourtrip.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The only WorkManager code: one unique `OneTimeWorkRequest` per trip ("trip-reminder-<uuid>", replaced on every
 * change), delayed until the reminder's time. WorkManager keeps it across restarts and app updates and may run it a
 * few minutes late (no exact-alarm permission needed).
 */
class WorkManagerReminderWork(context: Context, private val clock: Clock) : ReminderWork {
    private val workManager = WorkManager.getInstance(context)

    override fun schedule(tripUuid: String, uid: String, at: Instant) {
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
        workManager.cancelUniqueWork(uniqueName(tripUuid))
    }

    override fun cancelAll() {
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
    }
}
