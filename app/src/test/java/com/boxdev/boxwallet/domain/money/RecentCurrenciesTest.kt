package com.boxdev.boxwallet.domain.money

import com.boxdev.boxwallet.data.local.dao.CurrencyUsage
import java.util.Currency
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentCurrenciesTest {
    private val es = Locale("es", "GT")
    private val all = CurrencyCatalog.all(es)

    private fun usage(vararg codes: String) = codes.mapIndexed { i, c -> CurrencyUsage(c, 10 - i, 100L - i) }

    @Test
    fun `recent keeps the received order and leaves the full list without them`() {
        val s = pickerSections(all, usage("EUR", "GTQ", "USD"), "", es)
        assertEquals(listOf("EUR", "GTQ", "USD"), s.recent.map { it.currencyCode })
        assertTrue(s.all.none { it.currencyCode in setOf("EUR", "GTQ", "USD") })
        assertEquals(all.size - 3, s.all.size)
    }

    @Test
    fun `recent is capped at five`() {
        val s = pickerSections(all, usage("EUR", "GTQ", "USD", "MXN", "CRC", "HNL", "NIO"), "", es)
        assertEquals(5, s.recent.size)
    }

    @Test
    fun `query finds a currency by name without accents`() {
        val s = pickerSections(all, emptyList(), "quet", es)
        assertTrue(s.all.any { it.currencyCode == "GTQ" })
    }

    @Test
    fun `query finds by symbol`() {
        val codes = pickerSections(all, emptyList(), "₡", es).all.map { it.currencyCode }
        assertTrue("CRC" in codes)
        assertTrue("SVC" in codes)
    }

    @Test
    fun `query finds by code ignoring case`() {
        assertTrue(pickerSections(all, emptyList(), "eur", es).all.any { it.currencyCode == "EUR" })
    }

    @Test
    fun `with a query recent is empty and matches stay in all`() {
        val s = pickerSections(all, usage("GTQ"), "quet", es)
        assertTrue(s.recent.isEmpty())
        assertTrue(s.all.any { it.currencyCode == "GTQ" })
    }

    @Test
    fun `unknown or unsupported recent codes are skipped`() {
        val s = pickerSections(all, usage("ZZZ", "EUR"), "", es)
        assertEquals(listOf(Currency.getInstance("EUR")), s.recent)
    }
}
