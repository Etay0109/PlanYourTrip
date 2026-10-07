package com.example.planyourtrip

import android.content.Context
import android.content.res.Configuration
import com.example.planyourtrip.ui.common.AppLocale
import java.util.Locale

/**
 * A context whose resources use [AppLocale] (English strings, plurals and Material labels, English month names in
 * the pickers) and a left-to-right layout direction, whatever the phone's language. Also makes [AppLocale] the JVM
 * default for library code that reads `Locale.getDefault()`; Android resets that default when the system language
 * changes, so this runs for every new Activity and on every configuration change.
 */
fun Context.withAppLocale(): Context {
    applyDefaultAppLocale()
    val config = Configuration(resources.configuration).apply {
        setLocale(AppLocale.locale)
        setLayoutDirection(AppLocale.locale)
    }
    return createConfigurationContext(config)
}

fun applyDefaultAppLocale() {
    if (Locale.getDefault() != AppLocale.locale) Locale.setDefault(AppLocale.locale)
}
