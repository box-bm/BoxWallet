package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.RateSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Currency

class AccountBalanceTest {

    private val gtq = Currency.getInstance("GTQ")
    private val usd = Currency.getInstance("USD")

    private fun balance(currency: Currency, minor: Long, includeInTotal: Boolean = true) = AccountWithBalance(
        Account(name = "A", type = AccountType.CHECKING, currency = currency, createdAt = 0L, includeInTotal = includeInTotal),
        Money(minor, currency),
    )

    @Test
    fun sumsOnlyIncludedAccounts() = runTest {
        val total = totalBalance(
            listOf(balance(gtq, 10_000), balance(gtq, 5_000, includeInTotal = false)),
            gtq,
        ) { ExchangeRate.IDENTITY }
        assertEquals(TotalBalance(Money(10_000, gtq), approximate = false), total)
    }

    @Test
    fun convertsForeignAccountsWithTodayRate() = runTest {
        val total = totalBalance(listOf(balance(gtq, 10_000), balance(usd, 1_000)), gtq) { currency ->
            if (currency == usd) ExchangeRate(BigDecimal("7.75"), RateSource.AUTO) else ExchangeRate.IDENTITY
        }
        assertEquals(TotalBalance(Money(17_750, gtq), approximate = true), total)
    }

    @Test
    fun missingRateSkipsAccountAndFlagsApproximate() = runTest {
        val total = totalBalance(listOf(balance(gtq, 10_000), balance(usd, 1_000)), gtq) { currency ->
            if (currency == usd) null else ExchangeRate.IDENTITY
        }
        assertEquals(TotalBalance(Money(10_000, gtq), approximate = true), total)
    }

    @Test
    fun approximateRateFlagsApproximate() = runTest {
        val total = totalBalance(listOf(balance(usd, 1_000)), gtq) { ExchangeRate(BigDecimal("7.75"), RateSource.APPROXIMATE) }
        assertEquals(TotalBalance(Money(7_750, gtq), approximate = true), total)
    }
}
