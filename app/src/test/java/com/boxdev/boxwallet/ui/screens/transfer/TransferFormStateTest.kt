package com.boxdev.boxwallet.ui.screens.transfer

import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.AccountWithBalance
import com.boxdev.boxwallet.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.util.Currency

class TransferFormStateTest {

    private val gtq = Currency.getInstance("GTQ")
    private val usd = Currency.getInstance("USD")
    private val banco = AccountWithBalance(Account(id = 1, name = "Banco", type = AccountType.CHECKING, currency = gtq, createdAt = 0), Money(0, gtq))
    private val dolares = AccountWithBalance(Account(id = 2, name = "Dólares", type = AccountType.CHECKING, currency = usd, createdAt = 0), Money(0, usd))
    private val ahorro = AccountWithBalance(Account(id = 3, name = "Ahorro", type = AccountType.SAVINGS, currency = gtq, createdAt = 0), Money(0, gtq))

    private fun state(from: Long, to: Long, amount: String) = TransferFormState(
        fromAccountId = from,
        toAccountId = to,
        amount = amount,
        accounts = listOf(banco, dolares, ahorro),
    )

    @Test
    fun equivalentSetsManualRate() {
        val updated = state(from = 1, to = 2, amount = "771").withEquivalent("100")
        val expected = BigDecimal("100").divide(BigDecimal("771"), MathContext.DECIMAL64)
        assertEquals("100", updated.equivalent)
        assertEquals(0, expected.compareTo(BigDecimal(updated.manualRate)))
    }

    @Test
    fun manualRateSetsEquivalentInTheDestinationCurrency() {
        val updated = state(from = 1, to = 2, amount = "771").withManualRate("0.13")
        assertEquals("100.23", updated.equivalent)
    }

    @Test
    fun differentAccountsAndAPositiveAmountAreValid() {
        assertTrue(state(from = 1, to = 3, amount = "10").isValid)
    }

    @Test
    fun sameAccountIsInvalid() {
        assertFalse(state(from = 1, to = 1, amount = "10").isValid)
    }

    @Test
    fun zeroAmountIsInvalid() {
        assertFalse(state(from = 1, to = 3, amount = "0").isValid)
        assertFalse(state(from = 1, to = 3, amount = "").isValid)
    }

    @Test
    fun aMissingRateNeedsAManualOne() {
        val missing = state(from = 1, to = 2, amount = "771").copy(needsManualRate = true)
        assertFalse(missing.isValid)
        assertTrue(missing.withManualRate("0.13").isValid)
    }
}
