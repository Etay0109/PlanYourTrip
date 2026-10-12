package com.example.planyourtrip.data.currency

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * The free Frankfurter API v2 (https://frankfurter.dev, no API key): about 150 travel currencies. The only rates HTTP
 * code in the app. Rates are asked with base ILS and inverted ([FrankfurterJson.parseRates]).
 */
class FrankfurterRatesApi(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RatesApi {

    override suspend fun latestToIls(code: String): Pair<BigDecimal, LocalDate> =
        FrankfurterJson.parseRates(get("/v2/rates?base=$ILS&quotes=$code"), quote = code)

    override suspend fun currencies(): List<Currency> =
        FrankfurterJson.parseCurrencies(get("/v2/currencies"))

    private suspend fun get(path: String): String = withContext(ioDispatcher) {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("HTTP $status for $path")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val BASE_URL = "https://api.frankfurter.dev"
        const val TIMEOUT_MILLIS = 10_000
    }
}
