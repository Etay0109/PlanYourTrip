package com.example.planyourtrip

import android.content.Context
import com.example.planyourtrip.data.assistant.AssistantRepository
import com.example.planyourtrip.data.assistant.FirebaseAssistantRepository
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.auth.FirebaseAuthRepository
import com.example.planyourtrip.data.currency.CurrencyRepository
import com.example.planyourtrip.data.currency.FrankfurterRatesApi
import com.example.planyourtrip.data.currency.RoomCurrencyRepository
import com.example.planyourtrip.data.trip.ActivityRepository
import com.example.planyourtrip.data.trip.AppDatabase
import com.example.planyourtrip.data.trip.ExpenseRepository
import com.example.planyourtrip.data.trip.RoomActivityRepository
import com.example.planyourtrip.data.trip.RoomExpenseRepository
import com.example.planyourtrip.data.trip.RoomTripRepository
import com.example.planyourtrip.data.trip.TripRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.auth.FirebaseAuth
import java.time.Clock

/** Manual dependency container (no DI framework yet). One instance per process. */
class AppContainer(context: Context) {
    private val database: AppDatabase by lazy { AppDatabase.create(context) }

    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(FirebaseAuth.getInstance()) }

    val tripRepository: TripRepository by lazy { RoomTripRepository(database.tripDao()) }

    val expenseRepository: ExpenseRepository by lazy {
        RoomExpenseRepository(database.expenseDao(), database.tripDao())
    }

    val activityRepository: ActivityRepository by lazy {
        RoomActivityRepository(database.activityDao(), database.tripDao())
    }

    val clock: Clock = Clock.systemDefaultZone()

    val currencyRepository: CurrencyRepository by lazy {
        RoomCurrencyRepository(FrankfurterRatesApi(), database.exchangeRateDao(), clock)
    }

    /** Gemini Developer API through Firebase AI Logic (App Check is installed in PlanYourTripApplication). */
    val assistantRepository: AssistantRepository by lazy {
        FirebaseAssistantRepository(Firebase.ai(backend = GenerativeBackend.googleAI()))
    }
}
