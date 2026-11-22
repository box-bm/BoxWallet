package com.boxdev.boxwallet.data.local.entity

import com.boxdev.boxwallet.data.local.entity.TransactionType.ADJUSTMENT_IN
import com.boxdev.boxwallet.data.local.entity.TransactionType.ADJUSTMENT_OUT
import com.boxdev.boxwallet.data.local.entity.TransactionType.EXPENSE
import com.boxdev.boxwallet.data.local.entity.TransactionType.INCOME
import com.boxdev.boxwallet.data.local.entity.TransactionType.LOAN_IN
import com.boxdev.boxwallet.data.local.entity.TransactionType.LOAN_OUT
import com.boxdev.boxwallet.data.local.entity.TransactionType.TRANSFER_IN
import com.boxdev.boxwallet.data.local.entity.TransactionType.TRANSFER_OUT
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.toDomain
import com.boxdev.boxwallet.domain.model.toEntity
import com.boxdev.boxwallet.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTypeTest {
    @Test
    fun inflowTypes() {
        assertEquals(setOf(INCOME, ADJUSTMENT_IN, TRANSFER_IN, LOAN_IN), TransactionType.entries.filter { it.isInflow }.toSet())
    }

    @Test
    fun reportTypes() {
        assertEquals(setOf(INCOME, EXPENSE), TransactionType.entries.filter { it.countsInReports }.toSet())
    }

    @Test
    fun adjustmentTypes() {
        assertEquals(setOf(ADJUSTMENT_IN, ADJUSTMENT_OUT), TransactionType.entries.filter { it.isAdjustment }.toSet())
    }

    @Test
    fun transferHelpers() {
        assertTrue(TRANSFER_IN.isInflow)
        assertFalse(TRANSFER_OUT.isInflow)
        for (type in listOf(TRANSFER_IN, TRANSFER_OUT)) {
            assertTrue(type.isTransfer)
            assertFalse(type.countsInReports)
        }
        assertFalse(INCOME.isTransfer)
    }

    @Test
    fun loanHelpers() {
        assertTrue(LOAN_IN.isInflow)
        assertFalse(LOAN_OUT.isInflow)
        assertFalse(LOAN_IN.countsInReports)
        assertFalse(LOAN_OUT.countsInReports)
        assertTrue(LOAN_IN.isLoan)
        assertTrue(LOAN_OUT.isLoan)
        assertFalse(INCOME.isLoan)
    }

    @Test
    fun transferIdRoundTrips() {
        val usd = java.util.Currency.getInstance("USD")
        val tx = Transaction.singleCurrency(
            1, TRANSFER_OUT, java.time.LocalDateTime.of(2026, 1, 1, 0, 0), Money(500, usd),
        ).copy(transferId = "t-1")
        assertEquals("t-1", tx.toEntity().toDomain(usd, usd).transferId)
    }
}
