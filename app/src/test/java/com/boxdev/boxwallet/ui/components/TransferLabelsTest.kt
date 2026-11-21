package com.boxdev.boxwallet.ui.components

import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.TransferPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Currency

class TransferLabelsTest {

    private val banco = Account(id = 1, name = "Banco", type = AccountType.CHECKING, currency = Currency.getInstance("GTQ"), createdAt = 0)
    private val dolares = Account(id = 2, name = "Dólares", type = AccountType.SAVINGS, currency = Currency.getInstance("USD"), createdAt = 0)
    private val visa = Account(id = 3, name = "Visa BAC", type = AccountType.CREDIT_CARD, currency = Currency.getInstance("GTQ"), createdAt = 0)
    private val visaUsd = visa.copy(id = 4, currency = Currency.getInstance("USD"), parentAccountId = 3)
    private val accounts = listOf(banco, dolares, visa, visaUsd).associateBy { it.id }

    @Test
    fun namesBothAccountsFromSourceToDestination() {
        assertEquals("Banco → Dólares", transferTitle(TransferPair("t1", fromAccountId = 1, toAccountId = 2), accounts))
    }

    @Test
    fun unknownAccountGivesNoTitle() {
        assertNull(transferTitle(TransferPair("t1", fromAccountId = 1, toAccountId = 99), accounts))
    }

    @Test
    fun missingPairGivesNoTitle() {
        assertNull(transferTitle(null, accounts))
    }

    @Test
    fun cardSecondCurrencyIsNamedWithItsCard() {
        assertEquals("Banco → Visa BAC · USD", transferTitle(TransferPair("t1", fromAccountId = 1, toAccountId = 4), accounts))
        assertEquals("Visa BAC → Visa BAC · USD", transferTitle(TransferPair("t1", fromAccountId = 3, toAccountId = 4), accounts))
    }
}
