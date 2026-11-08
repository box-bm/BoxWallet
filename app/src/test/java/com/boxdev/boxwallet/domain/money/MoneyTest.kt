package com.boxdev.boxwallet.domain.money

import java.math.BigDecimal
import java.util.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {
    private val usd = Currency.getInstance("USD")
    private val jpy = Currency.getInstance("JPY")
    private val gtq = Currency.getInstance("GTQ")
    private val bhd = Currency.getInstance("BHD")

    @Test
    fun `minor digits`() {
        assertEquals(2, usd.minorDigits())
        assertEquals(0, jpy.minorDigits())
        assertEquals(3, bhd.minorDigits())
    }

    @Test
    fun `of rounds half even`() {
        assertEquals(100L, Money.of(BigDecimal("1.005"), usd).minor)
        assertEquals(102L, Money.of(BigDecimal("1.015"), usd).minor)
    }

    @Test
    fun `convert uses target digits`() {
        assertEquals(19632L, Money(150000, gtq).convert(usd, BigDecimal("0.130882")).minor)
        assertEquals(7230L, Money(1500, jpy).convert(gtq, BigDecimal("0.0482")).minor)
        assertEquals(1585L, Money(1000, usd).convert(jpy, BigDecimal("158.48096064")).minor)
    }

    @Test
    fun `plus rejects mixed currencies`() {
        assertThrows(IllegalArgumentException::class.java) { Money(1, usd) + Money(1, jpy) }
        assertEquals(Money(3, usd), Money(1, usd) + Money(2, usd))
    }

    @Test
    fun `parseMajor`() {
        assertEquals(123450L, parseMajor("1,234.5", usd)?.minor)
        assertNull(parseMajor("1.234", usd))
        assertNull(parseMajor("0", usd))
        assertEquals(1500L, parseMajor("1500", jpy)?.minor)
        assertNull(parseMajor("abc", usd))
    }
}
