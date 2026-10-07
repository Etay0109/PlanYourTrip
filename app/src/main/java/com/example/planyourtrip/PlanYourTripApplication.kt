package com.example.planyourtrip

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras

class PlanYourTripApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        applyDefaultAppLocale()
        // Before any Firebase AI request: App Check is enforced for Firebase AI Logic.
        installAppCheck()
        container = AppContainer(this)
        // Downloads and merges the signed-in account's data now and after every sign in; uploads local changes.
        container.syncManager.start()
        container.cleanUpDocumentFiles()
    }

    // Android resets the JVM default locale to the system language when it changes; keep it English.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyDefaultAppLocale()
    }
}

/** Used inside `viewModelFactory { initializer { ... } }` blocks to reach the [AppContainer]. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as PlanYourTripApplication).container
