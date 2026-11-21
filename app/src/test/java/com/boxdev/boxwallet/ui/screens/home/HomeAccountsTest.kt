package com.boxdev.boxwallet.ui.screens.home

import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.model.cardBalance
import com.boxdev.boxwallet.domain.model.cardSummary
import com.boxdev.boxwallet.domain.money.Money
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Currency

class HomeAccountsTest {

    private val usd = Currency.getInstance("USD")

    private fun account(id: Long) = AccountWithBalance(
        Account(id = id, name = "A$id", type = AccountType.CASH, currency = usd, createdAt = 0),
        Money(0, usd),
    )

    @Test
    fun oneAccountHidesTheRow() {
        assertFalse(showAccountsRow(listOf(account(1))))
    }

    @Test
    fun twoAccountsShowTheRow() {
        assertTrue(showAccountsRow(listOf(account(1), account(2))))
    }

    @Test
    fun tilesStayTallWhileACardHasASecondCurrencyEvenWithNothingOwed() {
        val gtq = Currency.getInstance("GTQ")
        val card = Account(id = 3, name = "Visa", type = AccountType.CREDIT_CARD, currency = gtq, createdAt = 0)
        val child = card.copy(id = 4, currency = usd, parentAccountId = 3)
        val row = listOf(account(1), AccountWithBalance(card, Money(-100_00, gtq)))
        fun summary(second: Money?) = cardSummary(
            card, cardBalance(card, Money(-100_00, gtq), null), second?.let { cardBalance(child, it, null) }, null, approximate = false,
        )

        assertFalse(tallAccountTiles(row, mapOf(3L to summary(null))))
        assertTrue(tallAccountTiles(row, mapOf(3L to summary(Money(0, usd)))))
        assertTrue(tallAccountTiles(row, mapOf(3L to summary(Money(-10_00, usd)))))
    }
}
