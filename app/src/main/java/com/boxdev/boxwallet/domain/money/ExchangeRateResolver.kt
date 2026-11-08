package com.boxdev.boxwallet.domain.money

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.Currency

/** Source of USD-based rates (units of a currency per 1 USD). */
interface RateLookup {
    suspend fun exact(day: LocalDate, code: String): BigDecimal?
    suspend fun nearest(day: LocalDate, code: String): BigDecimal?

    /** Fetches and stores the rates published for [day]. False when offline or no history. */
    suspend fun download(day: LocalDate): Boolean
}

/** Resolves the rate of a transaction by its date (spec 3.3). Null means ask for a manual rate. */
class ExchangeRateResolver(
    private val lookup: RateLookup,
    private val today: () -> LocalDate = { LocalDate.now() },
) {

    suspend fun resolve(day: LocalDate, from: Currency, to: Currency): ExchangeRate? {
        if (from == to) return ExchangeRate.IDENTITY
        val fromCode = from.currencyCode
        val toCode = to.currencyCode

        cross(fromCode, toCode) { code -> unitsPerUsd(code) { lookup.exact(day, code) } }
            ?.let { return ExchangeRate(it, RateSource.AUTO) }

        if (!day.isBefore(HISTORY_START) && !day.isAfter(today()) && lookup.download(day)) {
            cross(fromCode, toCode) { code -> unitsPerUsd(code) { lookup.exact(day, code) } }
                ?.let { return ExchangeRate(it, RateSource.AUTO) }
        }

        return cross(fromCode, toCode) { code -> unitsPerUsd(code) { lookup.nearest(day, code) } }
            ?.let { ExchangeRate(it, RateSource.APPROXIMATE) }
    }

    private suspend fun unitsPerUsd(code: String, find: suspend () -> BigDecimal?): BigDecimal? =
        if (code == USD) BigDecimal.ONE else find()

    private suspend fun cross(
        fromCode: String,
        toCode: String,
        units: suspend (String) -> BigDecimal?,
    ): BigDecimal? {
        val f = units(fromCode) ?: return null
        val t = units(toCode) ?: return null
        return t.divide(f, MathContext.DECIMAL64)
    }

    companion object {
        const val USD = "USD"
        val HISTORY_START: LocalDate = LocalDate.of(2024, 3, 2)
    }
}
