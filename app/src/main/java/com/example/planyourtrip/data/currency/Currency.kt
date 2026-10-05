package com.example.planyourtrip.data.currency

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** The shekel's ISO 4217 code. Every rate is "shekels per 1 unit" of another currency. */
const val ILS = "ILS"

/** A currency the rates service supports, e.g. ("EUR", "Euro"). */
data class Currency(val code: String, val name: String)

/** Cached exchange rate: 1 [code] = [ilsPerUnit] shekels. */
data class ExchangeRate(
    val code: String,
    val ilsPerUnit: BigDecimal,
    /** Date of the rate as returned by the service (not the day we fetched it). */
    val date: LocalDate,
    /** When this device fetched it; drives the 12-hour refresh. */
    val fetchedAt: Instant,
)
