package com.boxdev.boxwallet.data.local.entity

import com.boxdev.boxwallet.data.local.entity.TransactionType.ADJUSTMENT_IN
import com.boxdev.boxwallet.data.local.entity.TransactionType.ADJUSTMENT_OUT
import com.boxdev.boxwallet.data.local.entity.TransactionType.EXPENSE
import com.boxdev.boxwallet.data.local.entity.TransactionType.INCOME
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionTypeTest {
    @Test
    fun inflowTypes() {
        assertEquals(setOf(INCOME, ADJUSTMENT_IN), TransactionType.entries.filter { it.isInflow }.toSet())
    }

    @Test
    fun reportTypes() {
        assertEquals(setOf(INCOME, EXPENSE), TransactionType.entries.filter { it.countsInReports }.toSet())
    }

    @Test
    fun adjustmentTypes() {
        assertEquals(setOf(ADJUSTMENT_IN, ADJUSTMENT_OUT), TransactionType.entries.filter { it.isAdjustment }.toSet())
    }
}
