package com.example.planyourtrip.data.currency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

class RoomCurrencyRepository(
    private val api: RatesApi,
    private val dao: ExchangeRateDao,
    private val clock: Clock,
) : CurrencyRepository {

    /** Fetched once per process; the list rarely changes. */
    @Volatile private var cachedCurrencies: List<Currency>? = null

    override fun observeRate(code: String): Flow<ExchangeRate?> =
        dao.observe(code).map { it?.toExchangeRate() }

    override suspend fun refreshRate(code: String): Boolean = try {
        val (rate, date) = api.latestToIls(code)
        dao.upsert(ExchangeRateEntity(code, rate, date, Instant.now(clock)))
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    override suspend fun currencies(): List<Currency>? {
        cachedCurrencies?.let { return it }
        return try {
            api.currencies().also { cachedCurrencies = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
