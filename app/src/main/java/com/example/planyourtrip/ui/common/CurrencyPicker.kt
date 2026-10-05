package com.example.planyourtrip.ui.common

import com.example.planyourtrip.data.currency.Currency
import java.util.Locale

/** The currency picker sheet while it is open (trip form and trip screen). */
data class CurrencyPickerState(
    /** The currently chosen currency, checked in the list. */
    val selectedCode: String?,
    val query: String = "",
    /** Everything the service supports (empty until loaded). */
    val currencies: List<Currency> = emptyList(),
    /** [currencies] filtered by [query]. */
    val results: List<Currency> = emptyList(),
    val isLoading: Boolean = false,
    /** The list could not be loaded: message + "Try again". */
    val failed: Boolean = false,
) {
    fun withQuery(query: String) = copy(query = query, results = filterCurrencies(currencies, query))

    fun loading() = copy(isLoading = true, failed = false)

    /** The result of loading the list: null = failed. */
    fun loaded(list: List<Currency>?) =
        if (list == null) copy(isLoading = false, failed = true)
        else copy(isLoading = false, failed = false, currencies = list, results = filterCurrencies(list, query))
}

/** Currencies whose code or name contains [query] (case-insensitive); codes starting with it come first. */
fun filterCurrencies(currencies: List<Currency>, query: String): List<Currency> {
    val q = query.trim()
    if (q.isEmpty()) return currencies
    return currencies
        .filter { it.code.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
        .sortedBy { if (it.code.startsWith(q, ignoreCase = true)) 0 else 1 }
}

/**
 * Full name of [code] for display ("Euro"): from the service's [currencies] when loaded, else the platform's
 * name, else null.
 */
fun currencyName(code: String, currencies: List<Currency>, locale: Locale = Locale.getDefault()): String? =
    currencies.firstOrNull { it.code == code }?.name
        ?: runCatching { java.util.Currency.getInstance(code).getDisplayName(locale) }.getOrNull()?.takeIf { it != code }
