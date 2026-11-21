package com.boxdev.boxwallet.ui.screens.home

import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.AccountWithBalance
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
}
