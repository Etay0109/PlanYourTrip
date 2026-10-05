package com.example.planyourtrip.data.currency

import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

/** Exchange rates to shekels, cached locally. Callers never see network exceptions. */
interface CurrencyRepository {
    /** The cached rate for [code], or null when it was never fetched. */
    fun observeRate(code: String): Flow<ExchangeRate?>

    /** Fetches the latest rate for [code] and stores it. Returns false when it could not be fetched. */
    suspend fun refreshRate(code: String): Boolean

    /** Currencies the rates service supports, sorted by code, or null when they could not be fetched. */
    suspend fun currencies(): List<Currency>?
}

/** A cached rate is refreshed once it is this old (measured from when it was fetched). */
val RATE_MAX_AGE: Duration = Duration.ofHours(12)

/** True when there is no cached rate or it was fetched [RATE_MAX_AGE] or longer ago. */
fun isStale(fetchedAt: Instant?, now: Instant): Boolean =
    fetchedAt == null || Duration.between(fetchedAt, now) >= RATE_MAX_AGE
