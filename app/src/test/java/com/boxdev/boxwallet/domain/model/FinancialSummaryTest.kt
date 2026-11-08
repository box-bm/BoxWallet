package com.boxdev.boxwallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Amounts are minor units of the main currency. */
class FinancialSummaryTest {

    @Test
    fun `balance is income minus expense`() {
        val summary = FinancialSummary(totalIncome = 500000L, totalExpense = 300000L)
        assertEquals(200000L, summary.balance)
    }

    @Test
    fun `balance is negative when expenses exceed income`() {
        val summary = FinancialSummary(totalIncome = 100000L, totalExpense = 300000L)
        assertEquals(-200000L, summary.balance)
    }

    @Test
    fun `balance is zero when equal`() {
        val summary = FinancialSummary(totalIncome = 50000L, totalExpense = 50000L)
        assertEquals(0L, summary.balance)
    }

    @Test
    fun `balance defaults to zero`() {
        assertEquals(0L, FinancialSummary().balance)
    }

    @Test
    fun `monthlyBalance is monthlyIncome minus monthlyExpense`() {
        val summary = FinancialSummary(monthlyIncome = 200000L, monthlyExpense = 150000L)
        assertEquals(50000L, summary.monthlyBalance)
    }

    @Test
    fun `monthlyBalance negative`() {
        val summary = FinancialSummary(monthlyIncome = 50000L, monthlyExpense = 120000L)
        assertEquals(-70000L, summary.monthlyBalance)
    }

    @Test
    fun `monthlyBalance defaults to zero`() {
        assertEquals(0L, FinancialSummary().monthlyBalance)
    }
}
