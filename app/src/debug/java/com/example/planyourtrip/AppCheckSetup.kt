package com.example.planyourtrip

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use the App Check debug provider. On the first launch after an install (or after clearing the app's
 * data) it logs a new debug token at Debug level, tag `com.google.firebase.appcheck.debug.internal.DebugAppCheckProvider`:
 * "Enter this debug secret into the allow list in the Firebase Console for your project: <token>".
 * Register it in the Firebase console (App Check → Apps → Manage debug tokens). Never commit the token.
 */
fun installAppCheck() {
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
