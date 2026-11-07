package com.boxdev.boxwallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
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

    // remainingAmount / outstandingTotals

    @Test
    fun `remainingAmount is the unpaid installments`() {
        assertEquals(900.0, loan(paidInstallments = 3).remainingAmount, 0.001)
    }

    @Test
    fun `remainingAmount is zero once paid off, even if overpaid`() {
        assertEquals(0.0, loan(paidInstallments = 13).remainingAmount, 0.001)
    }

    @Test
    fun `outstanding totals keep what I owe and what I am owed apart`() {
        val owe = loan(totalAmount = 3600.0, totalInstallments = 12, paidInstallments = 5)
        val owed = loan(totalAmount = 900.0, totalInstallments = 6, paidInstallments = 2).copy(loanType = LoanType.INCOME)
        val totals = listOf(owe, owed).outstandingTotals()
        assertEquals(2100.0, totals.toPay, 0.001)
        assertEquals(600.0, totals.toCollect, 0.001)
    }

    @Test
    fun `outstanding totals ignore paid-off loans`() {
        val done = loan(paidInstallments = 12)
        assertEquals(LoanTotals(0.0, 0.0), listOf(done).outstandingTotals())
    }

    // settledAmount / nextDueDate / status

    @Test
    fun `settledAmount is the paid installments`() {
        assertEquals(300.0, loan(paidInstallments = 3).settledAmount, 0.001)
    }

    @Test
    fun `nextDueDate is the first unpaid installment on the schedule`() {
        assertEquals(LocalDate.of(2026, 1, 1), loan().nextDueDate)
        assertEquals(LocalDate.of(2026, 4, 1), loan(paidInstallments = 3).nextDueDate)
    }

    @Test
    fun `nextDueDate is null once complete`() {
        assertNull(loan(paidInstallments = 12).nextDueDate)
    }

    @Test
    fun `installmentDate follows frequency and interval`() {
        val biweekly = loan().copy(frequency = RecurrenceFrequency.WEEKLY, frequencyInterval = 2)
        assertEquals(LocalDate.of(2026, 1, 15), biweekly.installmentDate(2))
    }

    @Test
    fun `status is overdue only when the next installment is before today`() {
        val l = loan(paidInstallments = 3) // next due 2026-04-01
        assertEquals(LoanStatus.ACTIVE, l.status(LocalDate.of(2026, 4, 1)))
        assertEquals(LoanStatus.OVERDUE, l.status(LocalDate.of(2026, 4, 2)))
        assertEquals(LoanStatus.PAID, loan(paidInstallments = 12).status(LocalDate.of(2030, 1, 1)))
    }
}
