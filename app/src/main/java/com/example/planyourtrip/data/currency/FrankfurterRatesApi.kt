package com.example.planyourtrip.data.currency

import com.example.planyourtrip.data.http.HttpGet
import java.math.BigDecimal
import java.time.LocalDate

/**
 * The free Frankfurter API v2 (https://frankfurter.dev, no API key): about 150 travel currencies. The only rates HTTP
 * code in the app. Rates are asked with base ILS and inverted ([FrankfurterJson.parseRates]).
 */
class FrankfurterRatesApi(
    private val http: HttpGet,
    private val userAgent: String,
) : RatesApi {

    override suspend fun latestToIls(code: String): Pair<BigDecimal, LocalDate> =
        FrankfurterJson.parseRates(get("/v2/rates?base=$ILS&quotes=$code"), quote = code)

    override suspend fun currencies(): List<Currency> =
        FrankfurterJson.parseCurrencies(get("/v2/currencies"))

    private suspend fun get(path: String): String = http.get(BASE_URL + path, userAgent)

    private companion object {
        const val BASE_URL = "https://api.frankfurter.dev"
    }
}
