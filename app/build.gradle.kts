plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.planyourtrip"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.planyourtrip"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8 shrinks and obfuscates the code and drops unused resources; the app's own rules are in
            // proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    // The app is English only (see AppLocale): drop the libraries' translations, so built-in labels stay English.
    androidResources {
        localeFilters += "en"
    }
    // English only, so an App Bundle never needs per-language splits (the app's locale is fixed in AppLocaleSetup).
    bundle {
        language {
            enableSplit = false
        }
    }
    // Version checks off: versions are pinned for Kotlin 2.0.21 on purpose (see the comments in libs.versions.toml
    // and CLAUDE.md "Conventions").
    lint {
        lintConfig = file("lint.xml")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // ProcessLifecycleOwner: the full sync runs when the app comes to the foreground.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)
    // Parses the Frankfurter exchange-rate responses.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // Gemini through Firebase AI Logic (trip assistant); no API key in the app.
    implementation(libs.firebase.ai)
    // Cloud backup of trips, expenses and activities (data/sync/FirestoreCloudStore).
    implementation(libs.firebase.firestore)
    // App Check is enforced for Firebase AI Logic: debug provider in debug builds, Play Integrity in release.
    implementation(libs.firebase.appcheck)
    debugImplementation(libs.firebase.appcheck.debug)
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.kotlinx.coroutines.play.services)
    // Destination photos: image loading and the disk cache (PhotoImageLoader.kt, DestinationPhoto.kt).
    // 3.0.4 is the last Coil built with Kotlin 2.0.21 (3.1+ needs a newer Kotlin stdlib).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // HTTP for the rates, place search and Wikipedia (data/http/OkHttpGet) and the client Coil uses (same version as
    // coil-network-okhttp 3.0.4).
    implementation(libs.okhttp)
    // "Trip starts tomorrow" reminders, scheduled on the device (data/reminder/WorkManagerReminderWork).
    // 2.10.x is built with Kotlin 1.8 (2.11+ needs Kotlin 2.1).
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // A local HTTP server for OkHttpGetTest (same version as okhttp).
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    // Provides AndroidJUnitRunner (testInstrumentationRunner above).
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // MigrationTestHelper (Room schema migrations).
    androidTestImplementation(libs.androidx.room.testing)
    // Compose UI tests (layout checks on narrow screens and large fonts); the manifest adds their host activity.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // ui-test-junit4 brings espresso-core 3.5, which fails on API 36 (InputManager.getInstance is gone).
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}