package com.example.planyourtrip

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.planyourtrip.ui.navigation.AppNavHost
import com.example.planyourtrip.ui.theme.PlanYourTripTheme

class MainActivity : ComponentActivity() {
    // English + left to right on every screen, dialog, menu, sheet and picker (they all use this context).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlanYourTripTheme {
                AppNavHost()
            }
        }
    }
}
