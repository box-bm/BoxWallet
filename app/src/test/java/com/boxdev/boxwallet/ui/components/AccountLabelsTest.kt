package com.boxdev.boxwallet.ui.components

import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Currency

class AccountLabelsTest {

    private val gtq = Currency.getInstance("GTQ")
    private val usd = Currency.getInstance("USD")
    private val visa = Account(id = 5, name = "Visa BAC", type = AccountType.CREDIT_CARD, currency = gtq, createdAt = 0)
    private val visaUsd = visa.copy(id = 6, currency = usd, parentAccountId = 5)
    private val cash = Account(id = 1, name = "Cash", type = AccountType.CASH, currency = gtq, createdAt = 0)

    @Test
    fun aTopLevelAccountIsItsName() {
        assertEquals("Visa BAC", accountLabel(visa, null))
    }

    @Test
    fun aSecondCurrencyIsItsCardAndCode() {
        assertEquals("Visa BAC · USD", accountLabel(visaUsd, visa))
    }

    @Test
    fun childrenFollowTheirCard() {
        assertEquals(listOf(visa, visaUsd, cash), withChildrenAfterParents(listOf(visaUsd, visa, cash)))
    }
}
