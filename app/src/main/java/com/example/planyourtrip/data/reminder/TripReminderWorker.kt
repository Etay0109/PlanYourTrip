package com.example.planyourtrip.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.planyourtrip.PlanYourTripApplication

/** Fires a trip's reminder: [TripReminderNotifier] checks it is still wanted and posts it. Never retried. */
class TripReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val tripUuid = inputData.getString(KEY_TRIP_UUID) ?: return Result.success()
        val uid = inputData.getString(KEY_UID) ?: return Result.success()
        val container = (applicationContext as PlanYourTripApplication).container
        container.tripReminderNotifier.showIfDue(tripUuid, uid)
        return Result.success()
    }

    companion object {
        private const val KEY_TRIP_UUID = "tripUuid"
        private const val KEY_UID = "uid"

        fun input(tripUuid: String, uid: String): Data = workDataOf(KEY_TRIP_UUID to tripUuid, KEY_UID to uid)
    }
}
