package com.boxdev.boxwallet.data.rates

import com.boxdev.boxwallet.data.datastore.UserPreferences
import com.boxdev.boxwallet.data.local.dao.ExchangeRateDao
import com.boxdev.boxwallet.data.local.entity.ExchangeRateEntity
import com.boxdev.boxwallet.domain.money.RateLookup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.Currency
import javax.inject.Inject

class ExchangeRateRepository @Inject constructor(
    private val dao: ExchangeRateDao,
    private val api: ExchangeRateApi,
    private val prefs: UserPreferences,
) : RateLookup {

    override suspend fun exact(day: LocalDate, code: String): BigDecimal? =
        if (code == USD) BigDecimal.ONE else dao.rateOn(day.toEpochDay(), code)?.unitsPerUsd

    override suspend fun nearest(day: LocalDate, code: String): BigDecimal? {
        if (code == USD) return BigDecimal.ONE
        val d = day.toEpochDay()
        return (dao.rateOn(d, code) ?: dao.nearestBefore(d, code) ?: dao.nearestAfter(d, code))?.unitsPerUsd
    }

    override suspend fun download(day: LocalDate): Boolean {
        if (day.isBefore(ExchangeRateApi.HISTORY_START) || day.isAfter(LocalDate.now())) return false
        val snapshot = api.fetch(day) ?: return false
        store(snapshot)
        return true
    }

    suspend fun syncLatest(): Boolean {
        val snapshot = api.fetch() ?: return false
        store(snapshot)
        prefs.setLastRateSyncAt(System.currentTimeMillis())
        return true
    }

    /** Rate history of 1 [code] expressed in [main], oldest first. */
    fun series(code: String, main: Currency, from: LocalDate): Flow<List<Pair<LocalDate, BigDecimal>>> {
        val base = if (code == USD) main.currencyCode else code
        return dao.series(base, from.toEpochDay()).map { rows ->
            rows.mapNotNull { row ->
                val day = row.date
                val c = atOrBefore(day, code) ?: return@mapNotNull null
                val m = atOrBefore(day, main.currencyCode) ?: return@mapNotNull null
                LocalDate.ofEpochDay(day) to m.divide(c, MathContext.DECIMAL64)
            }
        }
    }

    /** Current rate of 1 [code] in [main] and the previous published day's, if any. */
    suspend fun latest(code: String, main: Currency): Pair<BigDecimal, BigDecimal?>? {
        val anchor = if (code == USD) main.currencyCode else code
        val newest = dao.nearestBefore(Long.MAX_VALUE, anchor) ?: return null
        val current = rateIn(newest.date, code, main) ?: return null
        val previousDay = dao.nearestBefore(newest.date, anchor)?.date
        return current to previousDay?.let { rateIn(it, code, main) }
    }

    private suspend fun rateIn(day: Long, code: String, main: Currency): BigDecimal? {
        val c = atOrBefore(day, code) ?: return null
        val m = atOrBefore(day, main.currencyCode) ?: return null
        return m.divide(c, MathContext.DECIMAL64)
    }

    private suspend fun atOrBefore(day: Long, code: String): BigDecimal? =
        if (code == USD) BigDecimal.ONE
        else (dao.rateOn(day, code) ?: dao.nearestBefore(day, code))?.unitsPerUsd

    private suspend fun store(snapshot: RateSnapshot) {
        val day = snapshot.date.toEpochDay()
        dao.upsertAll(snapshot.unitsPerUsd.map { (code, v) -> ExchangeRateEntity(day, code, v) })
    }

    private companion object {
        const val USD = "USD"
    }
}
