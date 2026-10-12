package com.example.planyourtrip.data.currency

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Parsers for Frankfurter v2 responses (pure, unit-tested). Rates are read from the JSON number's text straight into
 * [BigDecimal] (also "2.0e-06"), never through a Double. Malformed input throws, so a wrong rate is never cached.
 */
object FrankfurterJson {

    /**
     * Significant digits of an inverted rate. The service quotes about 5 digits per shekel ("VND 8446"), so 12 keep
     * every digit of 1 ÷ rate for currencies worth a millionth of a shekel (IRR) as well as for strong ones.
     */
    val INVERSION_PRECISION = MathContext(12, RoundingMode.HALF_UP)

    /**
     * `/v2/rates?base=ILS&quotes=ARS` = `[{"date":"2026-10-12","base":"ILS","quote":"ARS","rate":495.52}]` →
     * shekels per 1 ARS (1 ÷ 495.52) and the rate's date. Asking with base ILS keeps tiny currencies exact (the
     * service rounds what it returns, and an IRR quote in shekels would be 0.0000018). Throws when the answer isn't a
     * list (e.g. `{"status":422,"message":…}`), has no entry for base ILS and [quote], or a rate that isn't > 0.
     */
    fun parseRates(json: String, quote: String): Pair<BigDecimal, LocalDate> {
        val list = Json.parseToJsonElement(json) as? JsonArray
            ?: throw IllegalArgumentException("Not a list of rates")
        val entry = list.map { it.jsonObject }.firstOrNull {
            it["base"]?.jsonPrimitive?.content == ILS && it["quote"]?.jsonPrimitive?.content == quote
        } ?: throw IllegalArgumentException("No $ILS → $quote rate")
        val rate = number(entry["rate"] as? JsonPrimitive)
        require(rate.signum() > 0) { "Rate must be positive" }
        val date = LocalDate.parse(entry.getValue("date").jsonPrimitive.content)
        return BigDecimal.ONE.divide(rate, INVERSION_PRECISION).stripTrailingZeros() to date
    }

    /**
     * `/v2/currencies` = `[{"iso_code":"AED","name":"United Arab Emirates Dirham",…},…]` → the travel currencies
     * ([TravelCurrencies.isOffered]) sorted by code. An empty list throws, so it counts as a failure ("Try again")
     * instead of being kept in memory for the session.
     */
    fun parseCurrencies(json: String): List<Currency> {
        val list = (Json.parseToJsonElement(json) as? JsonArray ?: throw IllegalArgumentException("Not a list"))
            .mapNotNull { element ->
                val item = element.jsonObject
                val code = item["iso_code"]?.jsonPrimitive?.content?.trim() ?: return@mapNotNull null
                val name = item["name"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() } ?: code
                Currency(code, name).takeIf { TravelCurrencies.isOffered(code) }
            }
            .distinctBy { it.code }
            .sortedBy { it.code }
        require(list.isNotEmpty()) { "Empty currency list" }
        return list
    }

    /** A JSON number as an exact [BigDecimal] ("495.52", "8446", "2.0e-06"); a string or missing value throws. */
    private fun number(value: JsonPrimitive?): BigDecimal {
        requireNotNull(value) { "No rate" }
        require(!value.isString) { "Rate is not a number" }
        return BigDecimal(value.content)
    }
}

/** Which of the service's currencies a traveler can pick (pure, unit-tested). */
object TravelCurrencies {
    /**
     * Not money a traveler pays with: precious metals, the IMF's SDR, the COMESA unit of account, the offshore yuan
     * (CNY is offered) and the Crown dependencies' pounds (they use GBP; the service quotes them at par).
     */
    val NOT_FOR_TRAVEL: Set<String> = setOf(
        "XAU", "XAG", "XPD", "XPT", "XDR", "XSU", "XUA", "XBA", "XBB", "XBC", "XBD", "XTS", "XXX",
        "CMD", "CNH", "GGP", "IMP", "JEP",
    )

    /** Codes starting with X that are real currencies of several countries (the rest are metals or units). */
    val MULTI_COUNTRY: Set<String> = setOf("XAF", "XOF", "XCD", "XPF", "XCG")

    private val ISO_CODE = Regex("^[A-Z]{3}$")

    fun isOffered(code: String): Boolean =
        ISO_CODE.matches(code) && code !in NOT_FOR_TRAVEL && (!code.startsWith("X") || code in MULTI_COUNTRY)
}
