package com.example.planyourtrip.ui.common

import java.util.Locale

/**
 * The one locale the whole app uses, whatever the phone's language: dates ("Thu 12 Nov"), numbers, the Material
 * pickers and component labels are always US English and the layout is always left to right. Formatters take it as
 * their default instead of `Locale.getDefault()`; `withAppLocale()` applies it to the Activity's resources.
 */
object AppLocale {
    val locale: Locale = Locale.US
}
