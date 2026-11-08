package com.boxdev.boxwallet.ui.screens.transaction

import com.boxdev.boxwallet.domain.money.AmountResult
import com.boxdev.boxwallet.domain.money.ExchangeRateResolver
import com.boxdev.boxwallet.domain.money.RateLookup
import com.boxdev.boxwallet.domain.money.TransactionAmounts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency

class FormRateCacheTest {

    private val usd = Currency.getInstance("USD")
    private val eur = Currency.getInstance("EUR")
    private val gtq = Currency.getInstance("GTQ")
    private val day = LocalDate.of(2026, 11, 5)

    /** The exact day is missing, so every resolve also tries a download (an HTTP request in the app). */
    private class CountingLookup(private val gate: CompletableDeferred<Unit>? = null) : RateLookup {
        var downloads = 0
        override suspend fun exact(day: LocalDate, code: String): BigDecimal? = null
        override suspend fun nearest(day: LocalDate, code: String): BigDecimal? = when (code) {
            "EUR" -> BigDecimal("0.9")
            "GTQ" -> BigDecimal("7.65")
            "USD" -> BigDecimal.ONE
            else -> null
        }
        override suspend fun download(day: LocalDate): Boolean {
            downloads++
            gate?.await()
            return false
        }
    }

    private fun amounts(lookup: RateLookup) =
        TransactionAmounts(ExchangeRateResolver(lookup) { LocalDate.of(2026, 11, 7) })

    @Test
    fun `typing several amounts resolves the rate once`() = runTest {
        val lookup = CountingLookup()
        val cache = FormRateCache(amounts(lookup), backgroundScope)
        val key = FormRateCache.Key(eur, usd, gtq, day)

        repeat(5) { assertTrue(cache.rates(key) is AmountResult.Ready) }

        // One resolve per leg (EUR → USD, USD → GTQ), not per keystroke.
        assertEquals(2, lookup.downloads)
    }

    @Test
    fun `keystrokes during an in-flight resolve share it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val lookup = CountingLookup(gate)
        val cache = FormRateCache(amounts(lookup), backgroundScope)
        val key = FormRateCache.Key(eur, usd, gtq, day)

        // Each keystroke cancels the previous preview job, as the form does.
        var job = launch { cache.rates(key) }
        repeat(4) {
            advanceUntilIdle()
            job.cancel()
            job = launch { cache.rates(key) }
        }
        gate.complete(Unit)
        job.join()

        assertEquals(2, lookup.downloads)
    }

    @Test
    fun `a new date or currency resolves again`() = runTest {
        val lookup = CountingLookup()
        val cache = FormRateCache(amounts(lookup), backgroundScope)

        cache.rates(FormRateCache.Key(eur, usd, gtq, day))
        cache.rates(FormRateCache.Key(eur, usd, gtq, day.minusDays(1)))
        cache.rates(FormRateCache.Key(gtq, usd, gtq, day))

        assertEquals(6, lookup.downloads)
    }

    @Test
    fun `equivalent is computed locally from the cached rate`() = runTest {
        val cache = FormRateCache(amounts(CountingLookup()), backgroundScope)
        val ready = cache.rates(FormRateCache.Key(eur, usd, gtq, day)) as AmountResult.Ready

        // 1 EUR = 1/0.9 USD; 9.00 EUR → 10.00 USD.
        assertEquals("10.00", FormRateCache.equivalent("9", eur, usd, ready))
        assertEquals(null, FormRateCache.equivalent("", eur, usd, ready))
    }

    @Test
    fun `a missing rate is retried after the retry window, not on every keystroke`() = runTest {
        val lookup = CountingLookup()
        var now = 0L
        val cache = FormRateCache(amounts(lookup), backgroundScope) { now }
        // An unknown currency: no exact and no nearest rate, so the form needs a manual rate.
        val key = FormRateCache.Key(Currency.getInstance("JPY"), usd, usd, day)

        assertTrue(cache.rates(key) !is AmountResult.Ready)
        val first = lookup.downloads
        repeat(3) { cache.rates(key) }
        assertEquals(first, lookup.downloads)

        now += FormRateCache.RETRY_MS
        cache.rates(key)
        assertTrue(lookup.downloads > first)
    }
}
