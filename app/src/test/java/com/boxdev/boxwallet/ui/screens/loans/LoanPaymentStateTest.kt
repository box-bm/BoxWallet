package com.boxdev.boxwallet.ui.screens.loans

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Currency

class LoanPaymentStateTest {
    private val usd = Currency.getInstance("USD")
    private val now = LocalDateTime.of(2026, 3, 1, 10, 0)

    private fun state(amount: String, max: Long = 1000, editingId: Long? = null) =
        LoanPaymentState(amount = amount, date = now, max = Money(max, usd), editingId = editingId)

    @Test
    fun `amount above max is invalid`() {
        assertTrue(state("10.00").isValid)
        assertFalse(state("10.01").isValid)
    }

    @Test
    fun `zero is invalid`() {
        assertFalse(state("0").isValid)
        assertFalse(state("").isValid)
    }

    @Test
    fun `editing allows up to remaining plus the payment's own amount`() {
        val loan = Loan(
            id = 1, name = "Bank", loanType = LoanType.EXPENSE, total = Money(10_000, usd), accountId = 1,
            date = now.minusDays(30), paid = Money(9_000, usd),
        )
        val payment = Transaction.singleCurrency(1, TransactionType.LOAN_OUT, now, Money(4_000, usd)).copy(id = 7, loanId = 1)

        val editing = LoanPaymentState.forEdit(loan, payment)

        assertEquals(Money(5_000, usd), editing.max)
        assertEquals(7L, editing.editingId)
        assertEquals("40.00", editing.amount)
        assertTrue(editing.copy(amount = "50.00").isValid)
        assertFalse(editing.copy(amount = "50.01").isValid)
        assertEquals(Money(1_000, usd), LoanPaymentState.forNew(loan, Money(500, usd), now).max)
    }
}
