package com.example.planyourtrip.data.currency

import java.math.BigDecimal
import java.time.LocalDate

/** Rates service. Implementations throw on network or format errors. */
interface RatesApi {
    /** Shekels per 1 unit of [code] and the date of that rate. */
    suspend fun latestToIls(code: String): Pair<BigDecimal, LocalDate>

    /** The travel currencies the service offers, sorted by code. */
    suspend fun currencies(): List<Currency>
}
