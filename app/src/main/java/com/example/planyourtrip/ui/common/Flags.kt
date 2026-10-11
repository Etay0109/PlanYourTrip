package com.example.planyourtrip.ui.common

// Pure (unit-tested in FlagsTest): flag emoji for countries and currencies. Display only: nothing is stored or synced.

private val COUNTRY_CODE = Regex("^[A-Za-z]{2}$")

/**
 * The flag emoji of an ISO 3166 country code ("GB" → 🇬🇧, also "EU" → 🇪🇺): two regional indicator symbols. Null
 * for anything that isn't two letters, so a bad code never shows a wrong flag.
 */
fun countryFlag(countryCode: String?): String? {
    val code = countryCode?.trim()?.takeIf { it.matches(COUNTRY_CODE) }?.uppercase() ?: return null
    return code.map { String(Character.toChars(REGIONAL_INDICATOR_A + (it - 'A'))) }.joinToString("")
}

private const val REGIONAL_INDICATOR_A = 0x1F1E6

/**
 * The one fixed table of currency → flag (the country, or the EU for the euro). It covers every currency the rates
 * service offers today (see FlagsTest). A currency that isn't here gets no flag, never a guessed one.
 */
private val CURRENCY_COUNTRIES: Map<String, String> = mapOf(
    "AUD" to "AU", "BRL" to "BR", "CAD" to "CA", "CHF" to "CH", "CNY" to "CN", "CZK" to "CZ", "DKK" to "DK",
    "EUR" to "EU", "GBP" to "GB", "HKD" to "HK", "HUF" to "HU", "IDR" to "ID", "ILS" to "IL", "INR" to "IN",
    "ISK" to "IS", "JPY" to "JP", "KRW" to "KR", "MXN" to "MX", "MYR" to "MY", "NOK" to "NO", "NZD" to "NZ",
    "PHP" to "PH", "PLN" to "PL", "RON" to "RO", "SEK" to "SE", "SGD" to "SG", "THB" to "TH", "TRY" to "TR",
    "USD" to "US", "ZAR" to "ZA",
)

/**
 * Currencies the app may show that deliberately have no flag (none today). Add a code here instead of guessing a
 * country when a currency belongs to no single country.
 */
val CURRENCIES_WITHOUT_FLAG: Set<String> = emptySet()

/** The currencies that have a flag (for tests). */
val CURRENCIES_WITH_FLAG: Set<String> get() = CURRENCY_COUNTRIES.keys

/** The flag shown next to a currency code ("EUR" → 🇪🇺), or null when the table has none. */
fun currencyFlag(currencyCode: String?): String? =
    currencyCode?.trim()?.uppercase()?.let { CURRENCY_COUNTRIES[it] }?.let(::countryFlag)
