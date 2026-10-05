package com.example.planyourtrip.data.currency

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/**
 * Parsers for Frankfurter responses (pure, unit-tested). Rates are read from the JSON number's text
 * straight into [BigDecimal], never through a Double. Malformed input throws.
 */
object FrankfurterJson {

    /**
     * `{"amount":1.0,"base":"EUR","date":"2026-10-02","rates":{"ILS":3.4408}}` → (3.4408, 2026-10-02).
     * Throws when the response is for another base than [expectedBase], so a wrong rate is never cached.
     */
    fun parseLatest(json: String, expectedBase: String): Pair<BigDecimal, LocalDate> {
        val root = Json.parseToJsonElement(json).jsonObject
        val base = root["base"]?.jsonPrimitive?.content
        require(base == expectedBase) { "Asked for $expectedBase, got $base" }
        val date = LocalDate.parse(root.getValue("date").jsonPrimitive.content)
        val amount = BigDecimal(root.getValue("amount").jsonPrimitive.content)
        val rates = root.getValue("rates").jsonObject
        val ils = BigDecimal(checkNotNull(rates[ILS]) { "No ILS rate" }.jsonPrimitive.content)
        require(amount.signum() > 0 && ils.signum() > 0) { "Rate must be positive" }
        // The service quotes per `amount` units (1 by default).
        val perUnit = if (amount.compareTo(BigDecimal.ONE) == 0) ils else ils.divide(amount, MathContext.DECIMAL64)
        return perUnit.stripTrailingZeros() to date
    }

    /**
     * `{"AUD":"Australian Dollar",...}` → currencies sorted by code. An empty list throws, so it counts as a
     * failure ("Try again") instead of being kept in memory for the session.
     */
    fun parseCurrencies(json: String): List<Currency> {
        val list = Json.parseToJsonElement(json).jsonObject
            .map { (code, name) -> Currency(code, name.jsonPrimitive.content) }
            .sortedBy { it.code }
        require(list.isNotEmpty()) { "Empty currency list" }
        return list
    }
}
