package com.example.planyourtrip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.planyourtrip.ui.navigation.AppNavHost
import com.example.planyourtrip.ui.theme.PlanYourTripTheme

class MainActivity : ComponentActivity() {
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
