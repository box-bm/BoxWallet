package com.boxdev.boxwallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FinancialSummaryTest {

    @Test
    fun `balance is income minus expense`() {
        val summary = FinancialSummary(totalIncome = 5000.0, totalExpense = 3000.0)
        assertEquals(2000.0, summary.balance, 0.001)
    }

    @Test
    fun `balance is negative when expenses exceed income`() {
        val summary = FinancialSummary(totalIncome = 1000.0, totalExpense = 3000.0)
        assertEquals(-2000.0, summary.balance, 0.001)
    }

    @Test
    fun `balance is zero when equal`() {
        val summary = FinancialSummary(totalIncome = 500.0, totalExpense = 500.0)
        assertEquals(0.0, summary.balance, 0.001)
    }

    @Test
    fun `balance defaults to zero`() {
        assertEquals(0.0, FinancialSummary().balance, 0.001)
    }

    @Test
    fun `monthlyBalance is monthlyIncome minus monthlyExpense`() {
        val summary = FinancialSummary(monthlyIncome = 2000.0, monthlyExpense = 1500.0)
        assertEquals(500.0, summary.monthlyBalance, 0.001)
    }

    @Test
    fun `monthlyBalance negative`() {
        val summary = FinancialSummary(monthlyIncome = 500.0, monthlyExpense = 1200.0)
        assertEquals(-700.0, summary.monthlyBalance, 0.001)
    }

    @Test
    fun `monthlyBalance defaults to zero`() {
        assertEquals(0.0, FinancialSummary().monthlyBalance, 0.001)
    }
}
