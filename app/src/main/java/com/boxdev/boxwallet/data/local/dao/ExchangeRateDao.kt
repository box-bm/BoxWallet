package com.boxdev.boxwallet.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.boxdev.boxwallet.data.local.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExchangeRateDao {

    @Upsert
    suspend fun upsertAll(rates: List<ExchangeRateEntity>)

    @Query("SELECT * FROM exchange_rates WHERE date = :day AND currencyCode = :code")
    suspend fun rateOn(day: Long, code: String): ExchangeRateEntity?

    /** Latest rate published strictly before [day]. */
    @Query("SELECT * FROM exchange_rates WHERE date < :day AND currencyCode = :code ORDER BY date DESC LIMIT 1")
    suspend fun nearestBefore(day: Long, code: String): ExchangeRateEntity?

    /** Earliest rate published strictly after [day]. */
    @Query("SELECT * FROM exchange_rates WHERE date > :day AND currencyCode = :code ORDER BY date ASC LIMIT 1")
    suspend fun nearestAfter(day: Long, code: String): ExchangeRateEntity?

    @Query("SELECT * FROM exchange_rates WHERE currencyCode = :code AND date >= :fromDay ORDER BY date ASC")
    fun series(code: String, fromDay: Long): Flow<List<ExchangeRateEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM exchange_rates WHERE date = :day)")
    suspend fun hasDay(day: Long): Boolean
}
