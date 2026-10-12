package com.example.planyourtrip.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.planyourtrip.MainActivity
import com.example.planyourtrip.R
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.ui.theme.NotificationColors
import com.example.planyourtrip.withAppLocale
import java.time.Clock
import java.time.LocalDate

/**
 * Posts a trip's "trip starts tomorrow" notification (channel "Trip reminders") when it fires and is still wanted
 * ([shouldShowReminder]); the "Before the trip" list is counted now, not when it was scheduled. Tapping it opens
 * [MainActivity] with the trip's uuid and the account's uid ([ReminderLink]).
 */
class TripReminderNotifier(
    context: Context,
    private val authRepository: AuthRepository,
    private val trips: ReminderTrips,
    private val settings: ReminderSettings,
    private val clock: Clock,
) {
    /** English strings whatever the phone's language (see AppLocaleSetup). */
    private val context: Context = context.applicationContext.withAppLocale()

    suspend fun showIfDue(tripUuid: String, uid: String) {
        val trip = trips.trip(tripUuid, uid)
        val show = shouldShowReminder(
            reminderUid = uid,
            signedInUid = authRepository.currentUser.value?.uid,
            tripStart = trip?.startDate,
            today = LocalDate.now(clock),
            enabled = settings.enabled.value,
            notificationsAllowed = notificationsAllowed(),
        )
        if (!show || trip == null) return
        val message = when (val m = reminderMessage(trips.beforeTripCount(tripUuid, uid))) {
            ReminderMessage.NoList -> context.getString(R.string.reminder_no_list)
            ReminderMessage.AllDone -> context.getString(R.string.reminder_all_done)
            is ReminderMessage.ItemsLeft -> context.resources.getQuantityString(R.plurals.reminder_items_left, m.count, m.count)
        }
        createChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NotificationColors.brand)
            .setContentTitle(context.getString(R.string.reminder_title, reminderDestination(trip.destination, trip.countryCode)))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openTrip(tripUuid, uid))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // notificationsAllowed() is part of shouldShowReminder above
        NotificationManagerCompat.from(context).notify(tripUuid.hashCode(), notification)
    }

    private fun notificationsAllowed(): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openTrip(tripUuid: String, uid: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ReminderLink.ACTION_OPEN_TRIP)
            .putExtra(ReminderLink.EXTRA_TRIP_UUID, tripUuid)
            .putExtra(ReminderLink.EXTRA_UID, uid)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, tripUuid.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "trip_reminders"
    }
}

/** A tapped reminder: open [tripUuid] of the account [uid] on the Prep tab, if it is still theirs. */
data class ReminderLink(val uid: String, val tripUuid: String) {
    companion object {
        const val ACTION_OPEN_TRIP = "com.example.planyourtrip.OPEN_TRIP_REMINDER"
        const val EXTRA_TRIP_UUID = "tripUuid"
        const val EXTRA_UID = "uid"

        fun fromIntent(intent: Intent?): ReminderLink? {
            if (intent?.action != ACTION_OPEN_TRIP) return null
            val uid = intent.getStringExtra(EXTRA_UID) ?: return null
            val tripUuid = intent.getStringExtra(EXTRA_TRIP_UUID) ?: return null
            return ReminderLink(uid, tripUuid)
        }
    }
}
