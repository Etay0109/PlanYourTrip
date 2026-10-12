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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow

/** Android 13+ needs the user's permission to show notifications (trip reminders). */
fun Context.needsNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

/**
 * Returns a function that shows the system's notification permission question when it is still needed (Android 13+,
 * not granted) and returns whether it was shown. The answer doesn't matter: without it reminders are simply not
 * posted.
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Boolean {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (context.needsNotificationPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            true
        } else {
            false
        }
    }
}

/**
 * Shows the notification permission question when [requests] asks for it (after creating or joining a future trip),
 * then calls [onAsked] so it is never asked that way again. Never on app start.
 */
@Composable
fun NotificationPermissionHost(requests: Flow<Unit>, onAsked: () -> Unit) {
    val request = rememberNotificationPermissionRequest()
    val currentOnAsked by rememberUpdatedState(onAsked)
    LaunchedEffect(requests) {
        requests.collect { if (request()) currentOnAsked() }
    }
}
