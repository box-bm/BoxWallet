package com.boxdev.boxwallet.domain.money

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.util.Currency

class ExchangeRateResolverTest {

    private val gtq = Currency.getInstance("GTQ")
    private val eur = Currency.getInstance("EUR")
    private val day = LocalDate.of(2026, 11, 5)
    private val today = LocalDate.of(2026, 11, 7)

    private class FakeLookup(
        val exact: MutableMap<Pair<LocalDate, String>, BigDecimal> = mutableMapOf(),
        val nearest: Map<Pair<LocalDate, String>, BigDecimal> = emptyMap(),
        val onDownload: (MutableMap<Pair<LocalDate, String>, BigDecimal>) -> Boolean = { false },
    ) : RateLookup {
        var downloads = 0
        override suspend fun exact(day: LocalDate, code: String) = exact[day to code]
        override suspend fun nearest(day: LocalDate, code: String) = nearest[day to code]
        override suspend fun download(day: LocalDate): Boolean {
            downloads++
            return onDownload(exact)
        }
    }

    private fun resolver(l: RateLookup) = ExchangeRateResolver(l) { today }

    @Test
    fun `same currency is identity`() = runTest {
        assertEquals(ExchangeRate.IDENTITY, resolver(FakeLookup()).resolve(day, gtq, gtq))
    }

    @Test
    fun `cross rate via usd`() = runTest {
        val l = FakeLookup(exact = mutableMapOf(
            (day to "GTQ") to BigDecimal("7.64"), (day to "EUR") to BigDecimal("0.8976"),
        ))
        val r = resolver(l).resolve(day, gtq, eur)!!
        assertEquals(BigDecimal("0.8976").divide(BigDecimal("7.64"), MathContext.DECIMAL64), r.value)
        assertEquals(RateSource.AUTO, r.source)
        assertEquals(0, l.downloads)
    }

    @Test
    fun `usd side is one`() = runTest {
        val l = FakeLookup(exact = mutableMapOf((day to "GTQ") to BigDecimal("7.5")))
        val r = resolver(l).resolve(day, Currency.getInstance("USD"), gtq)!!
        assertEquals(0, BigDecimal("7.5").compareTo(r.value))
    }

    @Test
    fun `downloads missing day once`() = runTest {
        val l = FakeLookup(onDownload = {
            it[day to "GTQ"] = BigDecimal("7.64"); it[day to "EUR"] = BigDecimal("0.9"); true
        })
        val r = resolver(l).resolve(day, gtq, eur)!!
        assertEquals(RateSource.AUTO, r.source)
        assertEquals(1, l.downloads)
    }

    @Test
    fun `offline uses nearest as approximate`() = runTest {
        val l = FakeLookup(nearest = mapOf((day to "GTQ") to BigDecimal("8"), (day to "EUR") to BigDecimal("1")))
        val r = resolver(l).resolve(day, gtq, eur)!!
        assertEquals(RateSource.APPROXIMATE, r.source)
        assertEquals(0, BigDecimal("0.125").compareTo(r.value))
        assertEquals(1, l.downloads)
    }

    @Test
    fun `no rate at all returns null`() = runTest {
        assertNull(resolver(FakeLookup()).resolve(day, gtq, eur))
    }

    @Test
    fun `date before 2024-03-02 never downloads and is approximate`() = runTest {
        val old = LocalDate.of(2024, 3, 1)
        val l = FakeLookup(nearest = mapOf((old to "GTQ") to BigDecimal("8"), (old to "EUR") to BigDecimal("1")))
        val r = resolver(l).resolve(old, gtq, eur)!!
        assertEquals(RateSource.APPROXIMATE, r.source)
        assertEquals(0, l.downloads)
    }

    @Test
    fun `future date never downloads`() = runTest {
        val l = FakeLookup()
        assertNull(resolver(l).resolve(today.plusDays(1), gtq, eur))
        assertEquals(0, l.downloads)
    }
}
