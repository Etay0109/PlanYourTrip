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
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.ui.theme.NotificationColors
import com.example.planyourtrip.withAppLocale

/**
 * The Android side of a reminder: posts the "trip starts tomorrow" notification (channel "Trip reminders") that
 * [ReminderDelivery] decided to show. Tapping it opens [MainActivity] with the trip's uuid and the account's uid
 * ([ReminderLink]).
 */
class TripReminderNotifier(context: Context) : ReminderPoster {
    /** English strings whatever the phone's language (see AppLocaleSetup). */
    private val context: Context = context.applicationContext.withAppLocale()

    override fun canPost(): Boolean = notificationsAllowed()

    override fun post(trip: Trip, uid: String, message: ReminderMessage) {
        val text = when (message) {
            ReminderMessage.NoList -> context.getString(R.string.reminder_no_list)
            ReminderMessage.AllDone -> context.getString(R.string.reminder_all_done)
            is ReminderMessage.ItemsLeft ->
                context.resources.getQuantityString(R.plurals.reminder_items_left, message.count, message.count)
        }
        createChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NotificationColors.brand)
            .setContentTitle(context.getString(R.string.reminder_title, reminderDestination(trip.destination, trip.countryCode)))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openTrip(trip.uuid, uid))
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // ReminderDelivery posts only when canPost()
        NotificationManagerCompat.from(context).notify(trip.uuid.hashCode(), notification)
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
