package com.boxdev.boxwallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.boxdev.boxwallet.domain.money.Money
import java.util.Currency
import java.time.LocalDate
import java.time.LocalDateTime

class LoanTest {

    private val gtq = Currency.getInstance("GTQ")
    private fun q(minor: Long) = Money(minor, gtq)

    /** [totalAmount] in minor units of GTQ. */
    private fun loan(
        totalAmount: Long = 120_000,
        totalInstallments: Int = 12,
        paidInstallments: Int = 0
    ) = Loan(
        name = "Test Loan",
        totalAmount = q(totalAmount),
        totalInstallments = totalInstallments,
        startDate = LocalDateTime.of(2026, 1, 1, 3, 4, 5),
        paidInstallments = paidInstallments
    )

    // installmentAmount

    @Test
    fun `installmentAmount divides total by installments`() {
        assertEquals(q(10_000), loan().installmentAmount(1))
    }

    @Test
    fun `installmentAmount returns zero when totalInstallments is zero`() {
        assertEquals(q(0), loan(totalInstallments = 0).installmentAmount(1))
    }

    @Test
    fun `installmentAmount with uneven division gives the remainder to the last installment`() {
        val l = loan(totalAmount = 10_000, totalInstallments = 3)
        assertEquals(q(3_333), l.installmentAmount(1))
        assertEquals(q(3_333), l.installmentAmount(2))
        assertEquals(q(3_334), l.installmentAmount(3))
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
        assertEquals(q(90_000), loan(paidInstallments = 3).remainingAmount)
    }

    @Test
    fun `remainingAmount is zero once paid off, even if overpaid`() {
        assertEquals(q(0), loan(paidInstallments = 13).remainingAmount)
    }

    @Test
    fun `outstanding totals keep what I owe and what I am owed apart`() {
        // remainingMainMinor is the unpaid installments' real main-currency amount, from the DAO.
        val owe = loan(totalAmount = 360_000, totalInstallments = 12, paidInstallments = 5)
            .copy(remainingMainMinor = 210_000)
        val owed = loan(totalAmount = 90_000, totalInstallments = 6, paidInstallments = 2)
            .copy(loanType = LoanType.INCOME, remainingMainMinor = 60_000)
        val totals = listOf(owe, owed).outstandingTotals()
        assertEquals(210_000L, totals.toPay)
        assertEquals(60_000L, totals.toCollect)
    }

    @Test
    fun `outstanding totals ignore paid-off loans`() {
        val done = loan(paidInstallments = 12)
        assertEquals(LoanTotals(0, 0), listOf(done).outstandingTotals())
    }

    // settledAmount / nextDueDate / status

    @Test
    fun `settledAmount is the paid installments`() {
        assertEquals(q(30_000), loan(paidInstallments = 3).settledAmount)
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
