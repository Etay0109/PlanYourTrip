package com.example.planyourtrip.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.reminder.isSamsung

/** The app is already left out of battery optimization (then the battery screen is never offered). */
fun Context.isIgnoringBatteryOptimizations(): Boolean =
    getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true

/**
 * The one-time "Get trip reminders on time" screen (see [NotificationPermissionHost]): why the phone may hold
 * reminders back, Samsung's own steps, and "Open settings" to the battery optimization list (falling back to the
 * app's settings page). Never asks for the "ignore battery optimizations" permission itself.
 */
@Composable
fun BatteryTipDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
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
