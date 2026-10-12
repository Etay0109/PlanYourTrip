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
 * service offers (Frankfurter v2 after the travel filter, see FlagsTest), except the multi-country ones in
 * [CURRENCIES_WITHOUT_FLAG]. A currency that isn't here gets no flag, never a guessed one.
 */
private val CURRENCY_COUNTRIES: Map<String, String> = mapOf(
    "AED" to "AE", "AFN" to "AF", "ALL" to "AL", "AMD" to "AM", "AOA" to "AO", "ARS" to "AR", "AUD" to "AU",
    "AWG" to "AW", "AZN" to "AZ", "BAM" to "BA", "BBD" to "BB", "BDT" to "BD", "BHD" to "BH", "BIF" to "BI",
    "BMD" to "BM", "BND" to "BN", "BOB" to "BO", "BRL" to "BR", "BSD" to "BS", "BTN" to "BT", "BWP" to "BW",
    "BYN" to "BY", "BZD" to "BZ", "CAD" to "CA", "CDF" to "CD", "CHF" to "CH", "CLP" to "CL", "CNY" to "CN",
    "COP" to "CO", "CRC" to "CR", "CUP" to "CU", "CVE" to "CV", "CZK" to "CZ", "DJF" to "DJ", "DKK" to "DK",
    "DOP" to "DO", "DZD" to "DZ", "EGP" to "EG", "ERN" to "ER", "ETB" to "ET", "EUR" to "EU", "FJD" to "FJ",
    "FKP" to "FK", "GBP" to "GB", "GEL" to "GE", "GHS" to "GH", "GIP" to "GI", "GMD" to "GM", "GNF" to "GN",
    "GTQ" to "GT", "GYD" to "GY", "HKD" to "HK", "HNL" to "HN", "HTG" to "HT", "HUF" to "HU", "IDR" to "ID",
    "ILS" to "IL", "INR" to "IN", "IQD" to "IQ", "IRR" to "IR", "ISK" to "IS", "JMD" to "JM", "JOD" to "JO",
    "JPY" to "JP", "KES" to "KE", "KGS" to "KG", "KHR" to "KH", "KMF" to "KM", "KPW" to "KP", "KRW" to "KR",
    "KWD" to "KW", "KYD" to "KY", "KZT" to "KZ", "LAK" to "LA", "LBP" to "LB", "LKR" to "LK", "LRD" to "LR",
    "LSL" to "LS", "LYD" to "LY", "MAD" to "MA", "MDL" to "MD", "MGA" to "MG", "MKD" to "MK", "MMK" to "MM",
    "MNT" to "MN", "MOP" to "MO", "MRU" to "MR", "MUR" to "MU", "MVR" to "MV", "MWK" to "MW", "MXN" to "MX",
    "MYR" to "MY", "MZN" to "MZ", "NAD" to "NA", "NGN" to "NG", "NIO" to "NI", "NOK" to "NO", "NPR" to "NP",
    "NZD" to "NZ", "OMR" to "OM", "PAB" to "PA", "PEN" to "PE", "PGK" to "PG", "PHP" to "PH", "PKR" to "PK",
    "PLN" to "PL", "PYG" to "PY", "QAR" to "QA", "RON" to "RO", "RSD" to "RS", "RUB" to "RU", "RWF" to "RW",
    "SAR" to "SA", "SBD" to "SB", "SCR" to "SC", "SDG" to "SD", "SEK" to "SE", "SGD" to "SG", "SHP" to "SH",
    "SLE" to "SL", "SOS" to "SO", "SRD" to "SR", "SSP" to "SS", "STN" to "ST", "SVC" to "SV", "SYP" to "SY",
    "SZL" to "SZ", "THB" to "TH", "TJS" to "TJ", "TMT" to "TM", "TND" to "TN", "TOP" to "TO", "TRY" to "TR",
    "TTD" to "TT", "TWD" to "TW", "TZS" to "TZ", "UAH" to "UA", "UGX" to "UG", "USD" to "US", "UYU" to "UY",
    "UZS" to "UZ", "VES" to "VE", "VND" to "VN", "VUV" to "VU", "WST" to "WS", "YER" to "YE", "ZAR" to "ZA",
    "ZMW" to "ZM", "ZWG" to "ZW",
)

/**
 * Offered currencies that deliberately have no flag: each is the money of several countries or territories (Central
 * and West African CFA francs, East Caribbean dollar, CFP franc, Caribbean and Netherlands Antillean guilders). Add a
 * code here instead of guessing a country.
 */
val CURRENCIES_WITHOUT_FLAG: Set<String> = setOf("XAF", "XOF", "XCD", "XPF", "XCG", "ANG")

/** The currencies that have a flag (for tests). */
val CURRENCIES_WITH_FLAG: Set<String> get() = CURRENCY_COUNTRIES.keys

/** The flag shown next to a currency code ("EUR" → 🇪🇺), or null when the table has none. */
fun currencyFlag(currencyCode: String?): String? =
    currencyCode?.trim()?.uppercase()?.let { CURRENCY_COUNTRIES[it] }?.let(::countryFlag)
