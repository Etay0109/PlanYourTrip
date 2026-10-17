package com.example.planyourtrip.ui.common

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.planyourtrip.data.reminder.ReminderSetupRequest
import kotlinx.coroutines.flow.Flow

/** Android 13+ needs the user's permission to show notifications (trip reminders). */
fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/**
 * Returns a function that shows the system's notification permission question when it is still needed (Android 13+,
 * not granted) and returns whether it was shown. [onAnswered] runs once the question closes. The answer doesn't
 * matter: without it reminders are simply not posted.
 */
@Composable
fun rememberNotificationPermissionRequest(onAnswered: () -> Unit = {}): () -> Boolean {
    val context = LocalContext.current
    val currentOnAnswered by rememberUpdatedState(onAnswered)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { currentOnAnswered() }
    return {
        // needsNotificationPermission() is only true on Android 13+.
        if (context.needsNotificationPermission()) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            true
        } else {
            false
        }
    }
}

/**
 * Sets reminders up when [requests] asks for it (after creating or joining a future trip, or turning the reminders
 * on; never on app start): the notification permission question first, then [onAsked] so it is never asked that way
 * again; then, once the question is answered (or wasn't needed), the one-time battery screen when [wantsBatteryTip]
 * says this phone needs it ([BatteryTipHost]).
 */
@Composable
fun NotificationPermissionHost(
    requests: Flow<ReminderSetupRequest>,
    onAsked: () -> Unit,
    wantsBatteryTip: (manufacturer: String, exempt: Boolean) -> Boolean,
    onBatteryTipShown: () -> Unit,
) {
    val context = LocalContext.current
    var showBatteryTip by rememberSaveable { mutableStateOf(false) }
    val offerBatteryTip = {
        if (wantsBatteryTip(Build.MANUFACTURER, context.isIgnoringBatteryOptimizations())) {
            onBatteryTipShown()
            showBatteryTip = true
        }
    }
    val request = rememberNotificationPermissionRequest(onAnswered = offerBatteryTip)
    val currentOnAsked by rememberUpdatedState(onAsked)
    val currentOffer by rememberUpdatedState(offerBatteryTip)
    LaunchedEffect(requests) {
        requests.collect { setup ->
            if (setup.askPermission && request()) currentOnAsked() else currentOffer()
        }
    }
    if (showBatteryTip) BatteryTipDialog(onDismiss = { showBatteryTip = false })
}
