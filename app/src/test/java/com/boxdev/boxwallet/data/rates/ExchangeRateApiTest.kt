package com.boxdev.boxwallet.data.rates

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate

class ExchangeRateApiTest {

    private val payload = """{"date":"2026-11-07","usd":{"gtq":7.64040354,"btc":0.00001,"eur":0.8976434,"usd":1}}"""

    private class FakeTransport(private val responses: List<HttpResult>) : HttpTransport {
        val urls = mutableListOf<String>()
        override fun get(url: String): HttpResult {
            urls += url
            return responses[urls.size - 1]
        }
    }

    @Test
    fun `latest uses jsdelivr`() = runTest {
        val t = FakeTransport(listOf(HttpResult.Ok(payload)))
        val snap = ExchangeRateApi(t).fetch()
        assertEquals(
            listOf("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json"),
            t.urls,
        )
        assertNotNull(snap)
        assertEquals(LocalDate.of(2026, 11, 7), snap!!.date)
        assertEquals(BigDecimal("7.64040354"), snap.unitsPerUsd["GTQ"])
    }

    @Test
    fun `falls back to pages on 404 and on IOException`() = runTest {
        val expected = "https://2025-01-15.currency-api.pages.dev/v1/currencies/usd.json"
        for (first in listOf(HttpResult.Status(404), HttpResult.Failure(IOException("x")))) {
            val t = FakeTransport(listOf(first, HttpResult.Ok(payload)))
            val snap = ExchangeRateApi(t).fetch(LocalDate.of(2025, 1, 15))
            assertNotNull(snap)
            assertEquals(expected, t.urls[1])
        }
    }

    @Test
    fun `filters non ISO and non positive`() = runTest {
        val body = """{"date":"2026-11-07","usd":{"btc":1,"gtq":0,"eur":-1,"usd":1}}"""
        val snap = ExchangeRateApi(FakeTransport(listOf(HttpResult.Ok(body)))).fetch()
        assertEquals(setOf("USD"), snap!!.unitsPerUsd.keys)
    }

    @Test
    fun `malformed json returns null and reports`() = runTest {
        var reports = 0
        val t = FakeTransport(listOf(HttpResult.Ok("not json"), HttpResult.Ok("{bad")))
        val snap = ExchangeRateApi(t) { reports++ }.fetch()
        assertNull(snap)
        assertEquals(1, reports)
    }

    @Test
    fun `date before history returns null without requests`() = runTest {
        val t = FakeTransport(emptyList())
        assertNull(ExchangeRateApi(t).fetch(LocalDate.of(2024, 3, 1)))
        assertEquals(0, t.urls.size)
    }
}
