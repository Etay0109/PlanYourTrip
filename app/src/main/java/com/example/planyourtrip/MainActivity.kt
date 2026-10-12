package com.example.planyourtrip

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.planyourtrip.data.reminder.ReminderLink
import com.example.planyourtrip.ui.navigation.AppNavHost
import com.example.planyourtrip.ui.theme.PlanYourTripTheme

class MainActivity : ComponentActivity() {
    /** A tapped trip reminder, until AppNavHost has opened its trip (or found it isn't there any more). */
    private val reminderLink = MutableStateFlow<ReminderLink?>(null)

    // English + left to right on every screen, dialog, menu, sheet and picker (they all use this context).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh start: after a configuration change the link was already handled.
        if (savedInstanceState == null) reminderLink.value = ReminderLink.fromIntent(intent)
        setContent {
            PlanYourTripTheme {
                AppNavHost(reminderLink = reminderLink, onReminderLinkHandled = { reminderLink.value = null })
            }
        }
    }

    /** singleTop: a reminder tapped while the app is open arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ReminderLink.fromIntent(intent)?.let { reminderLink.value = it }
    }
}
