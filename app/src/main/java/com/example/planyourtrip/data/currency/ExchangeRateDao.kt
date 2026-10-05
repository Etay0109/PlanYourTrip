package com.example.planyourtrip.data.currency

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates WHERE currency_code = :code")
    fun observe(code: String): Flow<ExchangeRateEntity?>

    @Upsert
    suspend fun upsert(rate: ExchangeRateEntity)
}
