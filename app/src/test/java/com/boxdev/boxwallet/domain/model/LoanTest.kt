package com.boxdev.boxwallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class LoanTest {

    private fun loan(
        totalAmount: Double = 1200.0,
        totalInstallments: Int = 12,
        paidInstallments: Int = 0
    ) = Loan(
        name = "Test Loan",
        totalAmount = totalAmount,
        totalInstallments = totalInstallments,
        startDate = LocalDateTime.of(2026, 1, 1, 3, 4, 5),
        paidInstallments = paidInstallments
    )

    // installmentAmount

    @Test
    fun `installmentAmount divides total by installments`() {
        assertEquals(100.0, loan().installmentAmount, 0.001)
    }

    @Test
    fun `installmentAmount returns zero when totalInstallments is zero`() {
        assertEquals(0.0, loan(totalInstallments = 0).installmentAmount, 0.001)
    }

    @Test
    fun `installmentAmount with uneven division`() {
        val l = loan(totalAmount = 100.0, totalInstallments = 3)
        assertEquals(33.333, l.installmentAmount, 0.001)
    }

    // remainingInstallments

    @Test
    fun `remainingInstallments when none paid`() {
        assertEquals(12, loan().remainingInstallments)
    }

    @Test
    fun `remainingInstallments when some paid`() {
        assertEquals(7, loan(paidInstallments = 5).remainingInstallments)
    }

    @Test
    fun `remainingInstallments when all paid`() {
        assertEquals(0, loan(paidInstallments = 12).remainingInstallments)
    }

    // progressFraction

    @Test
    fun `progressFraction at zero`() {
        assertEquals(0f, loan().progressFraction, 0.001f)
    }

    @Test
    fun `progressFraction at halfway`() {
        assertEquals(0.5f, loan(paidInstallments = 6).progressFraction, 0.001f)
    }

    @Test
    fun `progressFraction at completion`() {
        assertEquals(1f, loan(paidInstallments = 12).progressFraction, 0.001f)
    }

    @Test
    fun `progressFraction with zero totalInstallments returns zero`() {
        assertEquals(0f, loan(totalInstallments = 0).progressFraction, 0.001f)
    }

    // isComplete

    @Test
    fun `isComplete false when not all paid`() {
        assertFalse(loan(paidInstallments = 5).isComplete)
    }

    @Test
    fun `isComplete true when all paid`() {
        assertTrue(loan(paidInstallments = 12).isComplete)
    }

    @Test
    fun `isComplete true when overpaid`() {
        assertTrue(loan(paidInstallments = 15).isComplete)
    }
}
