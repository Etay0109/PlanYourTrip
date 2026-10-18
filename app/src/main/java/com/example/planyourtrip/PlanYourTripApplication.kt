package com.example.planyourtrip

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader

class PlanYourTripApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        applyDefaultAppLocale()
        // Before any Firebase AI request: App Check is enforced for Firebase AI Logic.
        installAppCheck()
        container = AppContainer(this)
        // Follows the session: a full sync whenever the app comes to the foreground signed in (not when Android only
        // starts the process in the background) and after every sign in; uploads local changes.
        container.syncManager.start()
        // Keeps a "trip starts tomorrow" reminder for every future trip of the signed-in account.
        container.tripReminderScheduler.start()
        container.logLastExit(this)
        container.cleanUpDocumentFiles()
    }

    /** Coil's loader for the destination photos (User-Agent, disk cache). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        photoImageLoader(context, container.userAgent)

    // Android resets the JVM default locale to the system language when it changes; keep it English.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyDefaultAppLocale()
    }
}

/** Used inside `viewModelFactory { initializer { ... } }` blocks to reach the [AppContainer]. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[APPLICATION_KEY]) as PlanYourTripApplication).container
