package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Currency

class LoanTest {

    private val gtq = Currency.getInstance("GTQ")
    private fun q(minor: Long) = Money(minor, gtq)
    private val today = LocalDate.of(2026, 3, 10)

    private fun loan(
        total: Long = 120_000,
        paid: Long = 0,
        type: LoanType = LoanType.EXPENSE,
        plan: LoanPlan? = null,
        expectedDate: LocalDate? = null,
        remainingMain: Long = 0,
    ) = Loan(
        name = "Test Loan",
        loanType = type,
        total = q(total),
        accountId = 1,
        date = LocalDateTime.of(2026, 1, 1, 3, 4, 5),
        plan = plan,
        expectedDate = expectedDate,
        paid = q(paid),
        remainingMainMinor = remainingMain,
    )

    private val monthly12 = LoanPlan(12, RecurrenceFrequency.MONTHLY, 1, LocalDate.of(2026, 2, 1))

    @Test
    fun `remaining is total minus paid`() {
        assertEquals(q(70_000), loan(paid = 50_000).remaining)
    }

    @Test
    fun `settled when nothing remains`() {
        assertFalse(loan(paid = 0).isSettled)
        assertTrue(loan(paid = 120_000).isSettled)
    }

    @Test
    fun `deleting a payment reopens a settled loan`() {
        val l = loan()
        assertEquals(LoanStatus.PAID, l.copy(paid = l.total).status(today))
        val reopened = l.copy(paid = q(l.total.minor - 1))
        assertFalse(reopened.isSettled)
        assertEquals(q(1), reopened.remaining)
        assertEquals(LoanStatus.ACTIVE, reopened.status(today))
    }

    @Test
    fun `schedule is empty without a plan and nextDue is the first unpaid`() {
        assertTrue(loan().schedule().isEmpty())
        assertNull(loan().nextDue())
        val l = loan(plan = monthly12, paid = 15_000)
        assertEquals(2, l.nextDue()?.number)
        assertNull(loan(plan = monthly12, paid = 120_000).nextDue())
    }

    @Test
    fun `with a plan status is overdue when an installment is overdue`() {
        assertEquals(LoanStatus.OVERDUE, loan(plan = monthly12).status(today))
        assertEquals(LoanStatus.ACTIVE, loan(plan = monthly12).status(LocalDate.of(2026, 1, 20)))
    }

    @Test
    fun `without plan overdue only after expectedDate with something left`() {
        val l = loan(expectedDate = LocalDate.of(2026, 3, 1))
        assertEquals(LoanStatus.OVERDUE, l.status(today))
        assertEquals(LoanStatus.ACTIVE, l.status(LocalDate.of(2026, 3, 1)))
        assertEquals(LoanStatus.PAID, l.copy(paid = l.total).status(today))
        assertEquals(LoanStatus.ACTIVE, loan().status(today))
    }

    @Test
    fun `disbursement and payment types follow the direction`() {
        val lent = loan(type = LoanType.INCOME)
        assertEquals(TransactionType.LOAN_OUT, lent.disbursementType)
        assertEquals(TransactionType.LOAN_IN, lent.paymentType)
        val borrowed = loan(type = LoanType.EXPENSE)
        assertEquals(TransactionType.LOAN_IN, borrowed.disbursementType)
        assertEquals(TransactionType.LOAN_OUT, borrowed.paymentType)
    }

    @Test
    fun `outstanding totals keep directions apart and skip settled loans`() {
        val loans = listOf(
            loan(type = LoanType.EXPENSE, remainingMain = 5_000),
            loan(type = LoanType.EXPENSE, remainingMain = 2_000),
            loan(type = LoanType.INCOME, remainingMain = 7_000),
            loan(type = LoanType.INCOME, paid = 120_000, remainingMain = 9_999),
        )
        assertEquals(LoanTotals(toPay = 7_000, toCollect = 7_000), loans.outstandingTotals())
    }
}
