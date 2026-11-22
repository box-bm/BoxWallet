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

    @Test fun all_excludesNonCurrencyXCodesAndIsSorted() {
        val all = CurrencyCatalog.all(Locale.US)
        val codes = all.map { it.currencyCode }.toSet()
        for (code in listOf("XAU", "XAG", "XPT", "XPD", "XDR", "XXX", "XTS", "XSU", "XUA")) {
            assertFalse(code, code in codes)
        }
        for (code in listOf("XOF", "XAF", "XCD", "XPF")) assertTrue(code, code in codes)
        val names = all.map { CurrencyCatalog.displayName(it, Locale.US) }
        val collator = java.text.Collator.getInstance(Locale.US)
        assertEquals(names.sortedWith(collator), names)
    }

    @Test fun all_excludesRetiredCurrencies() {
        val codes = CurrencyCatalog.all(Locale("es")).map { it.currencyCode }.toSet()
        for (code in listOf("AFA", "ALK", "CSD", "ROL", "MZM", "ARL", "VEB", "VEF", "ZWD", "ZWL", "HRK", "BGN")) {
            assertFalse(code, code in codes)
        }
        for (code in listOf("USD", "EUR", "GTQ", "HNL", "NIO", "CRC", "PAB", "MXN", "AFN", "RON", "MZN")) {
            assertTrue(code, code in codes)
        }
    }

    @Test fun all_excludesFundCodes() {
        val codes = CurrencyCatalog.all(Locale.US).map { it.currencyCode }.toSet()
        for (code in listOf("BOV", "CHE", "CHW", "CLF", "COU", "MXV", "USN", "UYI", "UYW")) {
            assertFalse(code, code in codes)
        }
    }

    @Test fun all_keepsRetiredCodesAlreadyInUse() {
        val codes = CurrencyCatalog.all(Locale.US, keep = listOf("AFA", "ROL", "XAU", "nope")).map { it.currencyCode }
        assertTrue("AFA" in codes)
        assertTrue("ROL" in codes)
        // Non-currency and unknown codes are never resurrected.
        assertFalse("XAU" in codes)
        assertEquals(codes.distinct(), codes)
    }

    @Test fun circulatingCodes_areNotNonCurrencyCodes() {
        for (code in CurrencyCatalog.CIRCULATING_CODES) assertTrue(code, code.length == 3 && code.all { it in 'A'..'Z' })
        for (code in listOf("XAU", "XDR", "XXX", "XTS")) assertFalse(code, code in CurrencyCatalog.CIRCULATING_CODES)
    }

    @Test fun symbol_preferredOverrides() {
        assertEquals("Q", CurrencyCatalog.symbol(Currency.getInstance("GTQ"), Locale("es")))
        assertEquals("$", CurrencyCatalog.symbol(usd, Locale("es")))
        assertEquals("MX$", CurrencyCatalog.symbol(Currency.getInstance("MXN"), Locale.US))
    }

    @Test fun isSupported() {
        assertTrue(CurrencyCatalog.isSupported("GTQ"))
        assertFalse(CurrencyCatalog.isSupported("XAU"))
        assertFalse(CurrencyCatalog.isSupported("XXX"))
        assertFalse(CurrencyCatalog.isSupported("XDR"))
        assertTrue(CurrencyCatalog.isSupported("XOF"))
        assertTrue(CurrencyCatalog.isSupported("XAF"))
        assertTrue(CurrencyCatalog.isSupported("XCD"))
        assertTrue(CurrencyCatalog.isSupported("XPF"))
        assertFalse(CurrencyCatalog.isSupported("nope"))
    }

    @Test fun countries_containsGuatemalaAndIsSorted() {
        val list = CurrencyCatalog.countries(Locale.US)
        assertTrue(list.any { it.code == "GT" && it.currency.currencyCode == "GTQ" })
        val collator = java.text.Collator.getInstance(Locale.US)
        assertEquals(list.map { it.name }.sortedWith(collator), list.map { it.name })
    }
}
