package com.boxdev.boxwallet.util

import com.boxdev.boxwallet.domain.money.Money
import java.util.Currency
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyFormatterTest {
    private val gtq = Currency.getInstance("GTQ")
    private val usd = Currency.getInstance("USD")
    private val f = CurrencyFormatter(main = gtq, locale = Locale.US)

    @Test fun format_gtq() = assertEquals("Q1,830.35", f.format(Money(183035, gtq)))
    @Test fun format_negativeUsd() = assertEquals("−$12.00", f.format(Money(-1200, usd)))
    @Test fun format_jpyNoDecimals() {
        val s = f.format(Money(1500, Currency.getInstance("JPY")))
        assertTrue(s, s.endsWith("1,500"))
    }
    @Test fun format_bhdThreeDecimals() {
        val s = f.format(Money(1234, Currency.getInstance("BHD")))
        assertTrue(s, s.endsWith("1.234"))
    }
    @Test fun formatMain_usesMain() = assertEquals("Q1,830.35", f.formatMain(183035))
    @Test fun formatCompact_gtq() = assertEquals("Q120k", f.formatCompact(Money(120000_00, gtq)))
    @Test fun formatCompact_small() = assertEquals("Q950", f.formatCompact(Money(950_00, gtq)))
    @Test fun formatCompact_negativeMillions() =
        assertEquals("−Q3.4M", f.formatCompact(Money(-3_400_000_00, gtq)))

    private val eur = Currency.getInstance("EUR")
    @Test fun withEquivalent_foreign() =
        assertEquals("€20.00" to "≈ Q165.79", f.withEquivalent(Money(2000, eur), Money(16579, gtq)))
    @Test fun withEquivalent_sameCurrencyHasNoEquivalent() =
        assertEquals("Q10.00" to null, f.withEquivalent(Money(1000, gtq), Money(1000, gtq)))
    @Test fun withEquivalent_jpyNoDecimals() {
        val (original, eq) = f.withEquivalent(Money(1500, Currency.getInstance("JPY")), Money(8000, gtq))
        assertTrue(original, original.endsWith("1,500"))
        assertEquals("≈ Q80.00", eq)
    }
}
