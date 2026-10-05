package com.example.planyourtrip.data.currency

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Last known rate per currency. Rates are public, so the cache is shared by all accounts. */
@Entity(tableName = "exchange_rates")
data class ExchangeRateEntity(
    @PrimaryKey @ColumnInfo(name = "currency_code") val currencyCode: String,
    /** Shekels per 1 unit, stored as exact decimal text (see Converters). */
    val rate: BigDecimal,
    /** Epoch day of the rate's date from the service. */
    @ColumnInfo(name = "rate_date") val rateDate: LocalDate,
    /** Epoch millis of when this device fetched the rate. */
    @ColumnInfo(name = "fetched_at") val fetchedAt: Instant,
)

fun ExchangeRateEntity.toExchangeRate() = ExchangeRate(
    code = currencyCode,
    ilsPerUnit = rate,
    date = rateDate,
    fetchedAt = fetchedAt,
)
