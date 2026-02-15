package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
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
}
