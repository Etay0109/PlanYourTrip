package com.example.planyourtrip

import com.example.planyourtrip.data.currency.Currency
import com.example.planyourtrip.data.currency.CurrencyRepository
import com.example.planyourtrip.data.currency.ExchangeRate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * In-memory [CurrencyRepository] for unit tests. [nextRates] is what the "service" returns; set [offline]
 * to make every request fail. [refreshCalls] counts rate requests.
 */
class FakeCurrencyRepository(
    private val now: () -> Instant,
    var currencyList: List<Currency> = listOf(Currency("EUR", "Euro"), Currency("USD", "US Dollar")),
) : CurrencyRepository {
    /** The local cache, by code. */
    val cache = MutableStateFlow<Map<String, ExchangeRate>>(emptyMap())
    val nextRates = mutableMapOf<String, Pair<BigDecimal, LocalDate>>()
    var offline = false
    val refreshCalls = mutableListOf<String>()

    override fun observeRate(code: String): Flow<ExchangeRate?> = cache.map { it[code] }

    override suspend fun refreshRate(code: String): Boolean {
        refreshCalls += code
        val (rate, date) = nextRates[code] ?: return false
        if (offline) return false
        cache.update { it + (code to ExchangeRate(code, rate, date, now())) }
        return true
    }

    override suspend fun currencies(): List<Currency>? = if (offline) null else currencyList
}
