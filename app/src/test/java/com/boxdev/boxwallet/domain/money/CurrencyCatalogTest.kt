package com.boxdev.boxwallet.domain.money

import java.util.Currency
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyCatalogTest {
    private val usd = Currency.getInstance("USD")

    @Test fun forCountry_guatemala_isGtq() =
        assertEquals(Currency.getInstance("GTQ"), CurrencyCatalog.forCountry("GT"))

    @Test fun forCountry_elSalvador_isUsd() =
        assertEquals(usd, CurrencyCatalog.forCountry("SV"))

    // Panama: the JDK may report PAB or USD; both are acceptable.
    @Test fun forCountry_panama_isPabOrUsd() {
        val c = CurrencyCatalog.forCountry("PA")
        assertTrue(c == Currency.getInstance("PAB") || c == usd)
    }

    @Test fun forCountry_invalid_isNull() = assertNull(CurrencyCatalog.forCountry("ZZ"))

    @Test fun all_excludesXCodesAndIsSorted() {
        val all = CurrencyCatalog.all(Locale.US)
        assertFalse(all.any { it.currencyCode == "XAU" })
        assertFalse(all.any { it.currencyCode.startsWith("X") })
        val names = all.map { CurrencyCatalog.displayName(it, Locale.US) }
        val collator = java.text.Collator.getInstance(Locale.US)
        assertEquals(names.sortedWith(collator), names)
    }

    @Test fun symbol_preferredOverrides() {
        assertEquals("Q", CurrencyCatalog.symbol(Currency.getInstance("GTQ"), Locale("es")))
        assertEquals("$", CurrencyCatalog.symbol(usd, Locale("es")))
        assertEquals("MX$", CurrencyCatalog.symbol(Currency.getInstance("MXN"), Locale.US))
    }

    @Test fun isSupported() {
        assertTrue(CurrencyCatalog.isSupported("GTQ"))
        assertFalse(CurrencyCatalog.isSupported("XAU"))
        assertFalse(CurrencyCatalog.isSupported("nope"))
    }

    @Test fun countries_containsGuatemalaAndIsSorted() {
        val list = CurrencyCatalog.countries(Locale.US)
        assertTrue(list.any { it.code == "GT" && it.currency.currencyCode == "GTQ" })
        val collator = java.text.Collator.getInstance(Locale.US)
        assertEquals(list.map { it.name }.sortedWith(collator), list.map { it.name })
    }
}
