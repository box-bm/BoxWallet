package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LoanTypeTest {

    @Test
    fun `EXPENSE maps to TransactionType EXPENSE`() {
        assertEquals(TransactionType.EXPENSE, LoanType.EXPENSE.toTransactionType())
    }

    @Test
    fun `INCOME maps to TransactionType INCOME`() {
        assertEquals(TransactionType.INCOME, LoanType.INCOME.toTransactionType())
    }

    @Test
    fun `each direction has its own wording`() {
        assertNotEquals(LoanType.EXPENSE.displayNameRes, LoanType.INCOME.displayNameRes)
        assertNotEquals(LoanType.EXPENSE.remainingLabelRes, LoanType.INCOME.remainingLabelRes)
        assertNotEquals(LoanType.EXPENSE.settledLabelRes, LoanType.INCOME.settledLabelRes)
    }
}
