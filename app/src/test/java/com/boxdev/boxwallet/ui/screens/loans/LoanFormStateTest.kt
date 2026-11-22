package com.boxdev.boxwallet.ui.screens.loans

import com.boxdev.boxwallet.domain.model.Account
import com.boxdev.boxwallet.domain.model.AccountType
import com.boxdev.boxwallet.domain.model.RecurrenceFrequency
import com.boxdev.boxwallet.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Currency

class LoanFormStateTest {

    private val gtq = Currency.getInstance("GTQ")
    private val account = Account(id = 1, name = "Cash", type = AccountType.CASH, currency = gtq, createdAt = 0)

    private fun state(
        name: String = "Test",
        totalAmount: String = "1200",
        accountId: Long? = 1,
        hasPlan: Boolean = false,
        installments: String = "12",
        frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        frequencyInterval: String = "1",
        paid: Money? = null,
    ) = LoanFormState(
        name = name,
        totalAmount = totalAmount,
        accountId = accountId,
        accounts = listOf(account),
        hasPlan = hasPlan,
        installments = installments,
        frequency = frequency,
        frequencyInterval = frequencyInterval,
        paid = paid,
    )

    @Test
    fun `currency comes from the chosen account`() {
        assertEquals(gtq, state().currency)
    }

    @Test
    fun `valid without plan needs name, amount and account`() {
        assertTrue(state().isValid)
        assertFalse(state(name = "  ").isValid)
        assertFalse(state(totalAmount = "").isValid)
        assertFalse(state(totalAmount = "0").isValid)
        assertFalse(state(accountId = null).isValid)
    }

    @Test
    fun `installments are ignored without a plan`() {
        assertTrue(state(hasPlan = false, installments = "").isValid)
    }

    @Test
    fun `with plan needs installments greater than zero`() {
        assertTrue(state(hasPlan = true).isValid)
        assertFalse(state(hasPlan = true, installments = "").isValid)
        assertFalse(state(hasPlan = true, installments = "0").isValid)
    }

    @Test
    fun `custom months needs a positive interval`() {
        assertTrue(state(hasPlan = true, frequency = RecurrenceFrequency.CUSTOM_MONTHS, frequencyInterval = "3").isValid)
        assertFalse(state(hasPlan = true, frequency = RecurrenceFrequency.CUSTOM_MONTHS, frequencyInterval = "0").isValid)
        assertFalse(state(hasPlan = true, frequency = RecurrenceFrequency.CUSTOM_MONTHS, frequencyInterval = "").isValid)
    }

    @Test
    fun `total below what was paid is invalid`() {
        assertFalse(state(totalAmount = "500", paid = Money(600_00, gtq)).isValid)
        assertTrue(state(totalAmount = "600", paid = Money(600_00, gtq)).isValid)
    }

    @Test
    fun `installmentAmount only with a plan`() {
        assertEquals(Money(100_00, gtq), state(hasPlan = true).installmentAmount)
        assertNull(state(hasPlan = false).installmentAmount)
        assertNull(state(hasPlan = true, installments = "0").installmentAmount)
    }
}
