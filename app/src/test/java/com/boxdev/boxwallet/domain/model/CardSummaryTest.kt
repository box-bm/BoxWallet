package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.util.Currency

class CardSummaryTest {

    private val gtq = Currency.getInstance("GTQ")
    private val usd = Currency.getInstance("USD")

    private fun card(limitMinor: Long? = 1000000) = Account(
        id = 1, name = "Visa BAC", type = AccountType.CREDIT_CARD, currency = gtq, createdAt = 0L,
        creditLimitMinor = limitMinor,
    )

    private val child = Account(
        id = 2, name = "Visa BAC", type = AccountType.CREDIT_CARD, currency = usd, createdAt = 0L, parentAccountId = 1,
    )

    @Test
    fun availableSingleCurrency() {
        val card = card()
        val summary = cardSummary(card, cardBalance(card, Money(-250000, gtq), null), null, null, approximate = false)

        assertEquals(250000L, summary.primary.usedMinor)
        assertEquals(Money(1000000, gtq), summary.limit)
        assertEquals(Money(750000, gtq), summary.available)
        assertFalse(summary.availableApproximate)
    }

    @Test
    fun availableDualCurrency() {
        val card = card()
        val summary = cardSummary(
            card,
            cardBalance(card, Money(-250000, gtq), null),
            cardBalance(child, Money(-10000, usd), null),
            BigDecimal("7.64"),
            approximate = true,
        )

        assertEquals(10000L, summary.second?.usedMinor)
        assertEquals(Money(1000000 - 250000 - 76400, gtq), summary.available)
        assertTrue(summary.availableApproximate)
    }

    @Test
    fun noRateMeansNoAvailable() {
        val card = card()
        val summary = cardSummary(
            card, cardBalance(card, Money(-250000, gtq), null), cardBalance(child, Money(-10000, usd), null),
            null, approximate = false,
        )

        assertEquals(Money(1000000, gtq), summary.limit)
        assertNull(summary.available)
    }

    @Test
    fun noLimitMeansNoAvailable() {
        val card = card(limitMinor = null)
        val summary = cardSummary(card, cardBalance(card, Money(-250000, gtq), null), null, null, approximate = false)

        assertNull(summary.limit)
        assertNull(summary.available)
        assertEquals(250000L, summary.primary.usedMinor)
    }

    @Test
    fun creditBalanceUsesZero() {
        val card = card()
        val summary = cardSummary(card, cardBalance(card, Money(5000, gtq), null), null, null, approximate = false)

        assertEquals(0L, summary.primary.usedMinor)
        assertEquals(Money(1000000, gtq), summary.available)
    }

    @Test
    fun secondUsedOnlyWhenInUse() {
        val card = card()
        fun summary(secondMinor: Long?) = cardSummary(
            card, cardBalance(card, Money(-250000, gtq), null),
            secondMinor?.let { cardBalance(child, Money(it, usd), null) }, null, approximate = false,
        )

        assertEquals(Money(250000, gtq), summary(null).primaryUsed)
        assertNull(summary(null).secondUsed)
        assertNull(summary(0).secondUsed)
        // A credit balance in the second currency is nothing used.
        assertNull(summary(500).secondUsed)
        assertEquals(Money(1000, usd), summary(-1000).secondUsed)
    }
}
