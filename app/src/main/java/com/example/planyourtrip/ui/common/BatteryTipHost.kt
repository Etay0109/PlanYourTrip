package com.example.planyourtrip.ui.common

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.reminder.canUseExactAlarms
import com.example.planyourtrip.data.reminder.isSamsung

/** The app is already left out of battery optimization (then the battery screen is never offered). */
fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

/**
 * The one-time "Get trip reminders on time" screen (see [NotificationPermissionHost]): why the phone may hold
 * reminders back, Samsung's own steps, and "Open settings" to the battery optimization list (falling back to the
 * app's settings page). When exact alarms aren't allowed (off by default from Android 14) it also offers "Alarms &
 * reminders", without which the reminder may come up to an hour late. Never asks for the "ignore battery
 * optimizations" permission itself.
 */
@Composable
fun BatteryTipDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exactAllowed = remember { context.getSystemService(AlarmManager::class.java).canUseExactAlarms() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_tip_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.battery_tip_body))
                if (isSamsung(Build.MANUFACTURER)) {
                    Text(
                        stringResource(R.string.battery_tip_samsung_steps),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Text(stringResource(R.string.battery_tip_exact_alarms))
                    TextButton(onClick = {
                        onDismiss()
                        context.openExactAlarmSettings()
                    }) { Text(stringResource(R.string.battery_tip_open_alarms)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                context.openBatterySettings()
            }) { Text(stringResource(R.string.battery_tip_open_settings)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.battery_tip_not_now)) } },
    )
}

/** "Alarms & reminders" for this app (Android 12+), falling back to the app's settings page. */
@RequiresApi(Build.VERSION_CODES.S)
private fun Context.openExactAlarmSettings() {
    val alarms = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.fromParts("package", packageName, null))
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    try {
        startActivity(alarms)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(appDetails)
        } catch (_: ActivityNotFoundException) {
            // No settings screen on this phone: nothing to open.
        }
    }
}

private fun Context.openBatterySettings() {
    val batteryList = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
    try {
        startActivity(batteryList)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(appDetails)
        } catch (_: ActivityNotFoundException) {
            // No settings screen on this phone: nothing to open.
        }
    }
}
