package com.example.planyourtrip

import android.content.Context
import com.example.planyourtrip.data.assistant.AssistantRepository
import com.example.planyourtrip.data.assistant.FirebaseAssistantRepository
import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.auth.FirebaseAuthRepository
import com.example.planyourtrip.data.currency.CurrencyRepository
import com.example.planyourtrip.data.currency.FrankfurterRatesApi
import com.example.planyourtrip.data.currency.RoomCurrencyRepository
import com.example.planyourtrip.data.sync.AndroidNetworkMonitor
import com.example.planyourtrip.data.sync.FirestoreCloudStore
import com.example.planyourtrip.data.sync.RoomSyncLocalStore
import com.example.planyourtrip.data.sync.SyncController
import com.example.planyourtrip.data.sync.SyncManager
import com.example.planyourtrip.data.trip.ActivityRepository
import com.example.planyourtrip.data.trip.AppDatabase
import com.example.planyourtrip.data.trip.ExpenseRepository
import com.example.planyourtrip.data.trip.RoomActivityRepository
import com.example.planyourtrip.data.trip.RoomExpenseRepository
import com.example.planyourtrip.data.trip.RoomTripRepository
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.common.share.AndroidTripPdfExporter
import com.example.planyourtrip.ui.common.share.TripPdfExporter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock

/** Manual dependency container (no DI framework yet). One instance per process. */
class AppContainer(context: Context) {
    private val database: AppDatabase by lazy { AppDatabase.create(context) }

    val authRepository: AuthRepository by lazy { FirebaseAuthRepository(FirebaseAuth.getInstance()) }

    val clock: Clock = Clock.systemDefaultZone()

    /** Lives as long as the process; runs the cloud sync. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Room ⇄ Firestore. Started in PlanYourTripApplication; the repositories report every local change to it. */
    val syncManager: SyncManager by lazy {
        SyncManager(
            authRepository = authRepository,
            cloud = FirestoreCloudStore.create(FirebaseFirestore.getInstance()),
            local = RoomSyncLocalStore(database),
            network = AndroidNetworkMonitor(context),
            scope = appScope,
        )
    }

    /** What ViewModels see of the sync (status, sign out). */
    val syncController: SyncController get() = syncManager

    val tripRepository: TripRepository by lazy { RoomTripRepository(database.tripDao(), clock, syncManager) }

    val expenseRepository: ExpenseRepository by lazy {
        RoomExpenseRepository(database.expenseDao(), database.tripDao(), clock, syncManager)
    }

    val activityRepository: ActivityRepository by lazy {
        RoomActivityRepository(database.activityDao(), database.tripDao(), clock, syncManager)
    }

    val currencyRepository: CurrencyRepository by lazy {
        RoomCurrencyRepository(FrankfurterRatesApi(), database.exchangeRateDao(), clock)
    }

    /** Gemini Developer API through Firebase AI Logic (App Check is installed in PlanYourTripApplication). */
    val assistantRepository: AssistantRepository by lazy {
        FirebaseAssistantRepository(Firebase.ai(backend = GenerativeBackend.googleAI()))
    }

    /** Draws the shared trip PDF (PdfDocument) and hands it out through the FileProvider. */
    val tripPdfExporter: TripPdfExporter by lazy { AndroidTripPdfExporter(context) }
}
