package com.boxdev.boxwallet.ui.screens.analytics

import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.boxdev.boxwallet.domain.money.ExchangeRate
import com.boxdev.boxwallet.domain.money.Money
import java.util.Currency
import com.boxdev.boxwallet.domain.model.inReports
import com.boxdev.boxwallet.domain.model.net
import java.time.LocalDate

class ReportMathTest {

    private val novStart = LocalDate.of(2026, 11, 1)
    private val novEnd = LocalDate.of(2026, 11, 30)
    private val octStart = LocalDate.of(2026, 10, 1)
    private val octEnd = LocalDate.of(2026, 10, 31)

    // comparisonRange

    @Test
    fun `month in progress compares with the same elapsed days of the previous month`() {
        val range = comparisonRange(novStart, novEnd, octStart, octEnd, today = LocalDate.of(2026, 11, 6))
        assertTrue(range.toDate)
        assertEquals(octStart, range.start)
        assertEquals(LocalDate.of(2026, 10, 6), range.end)
    }

    @Test
    fun `finished month compares with the whole previous month`() {
        val range = comparisonRange(octStart, octEnd, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), today = novStart)
        assertFalse(range.toDate)
        assertEquals(LocalDate.of(2026, 9, 30), range.end)
    }

    @Test
    fun `last day of the period counts as complete`() {
        val range = comparisonRange(novStart, novEnd, octStart, octEnd, today = novEnd)
        assertFalse(range.toDate)
        assertEquals(octEnd, range.end)
    }

    @Test
    fun `clipped range never runs past a shorter previous period`() {
        // March 30 against February: February has only 28 days in 2026.
        val range = comparisonRange(
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31),
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28),
            today = LocalDate.of(2026, 3, 30),
        )
        assertTrue(range.toDate)
        assertEquals(LocalDate.of(2026, 2, 28), range.end)
    }

    @Test
    fun `adjustments never count in monthly income or expense`() {
        val month = java.time.YearMonth.of(2026, 11)
        val list = listOf(
            installment(1, 1, LocalDate.of(2026, 11, 2), 1_000, TransactionType.INCOME),
            installment(2, 1, LocalDate.of(2026, 11, 3), 300, TransactionType.EXPENSE),
            installment(3, 1, LocalDate.of(2026, 11, 4), 5_000, TransactionType.ADJUSTMENT_IN),
            installment(4, 1, LocalDate.of(2026, 11, 5), 200, TransactionType.ADJUSTMENT_OUT),
        )

        val data = list.monthlyTotals(listOf(month)).single()

        assertEquals(1_000L, data.income)
        assertEquals(300L, data.expense)
        assertEquals(2, list.inReports().size)
        assertEquals(700L, list.net())
    }

    private fun installment(
        id: Long,
        loanId: Long,
        day: LocalDate,
        /** Minor units of the main currency (GTQ). */
        amount: Long,
        type: TransactionType = TransactionType.EXPENSE,
        paid: Boolean = false,
    ): Transaction {
        val money = Money(amount, Currency.getInstance("GTQ"))
        return Transaction(
            id = id,
            accountId = 1,
            type = type,
            date = day.atTime(9, 0),
            original = money,
            accountAmount = money,
            accountRate = ExchangeRate.IDENTITY,
            amount = money,
            mainRate = ExchangeRate.IDENTITY,
            isCleared = paid,
            loanId = loanId,
        )
    }

    // loanFlows

    private val gtq = Currency.getInstance("GTQ")
    private fun loan(id: Long, type: com.boxdev.boxwallet.domain.model.LoanType, total: Long, paid: Long = 0, remainingMain: Long = total - paid) =
        com.boxdev.boxwallet.domain.model.Loan(
            id = id, name = "L$id", loanType = type, total = Money(total, gtq), accountId = 1,
            date = LocalDate.of(2026, 11, 1).atTime(9, 0), paid = Money(paid, gtq), remainingMainMinor = remainingMain,
        )

    @Test
    fun `loan flows split by direction and role`() {
        // Received 10,000 to repay 11,000: "received" is the disbursement movement, not the total.
        val borrowed = loan(1, com.boxdev.boxwallet.domain.model.LoanType.EXPENSE, 11_000)
        val lent = loan(2, com.boxdev.boxwallet.domain.model.LoanType.INCOME, 1_000)
        val flows = loanFlows(
            listOf(
                installment(1, 1, LocalDate.of(2026, 11, 1), 10_000, TransactionType.LOAN_IN),
                installment(2, 1, LocalDate.of(2026, 11, 2), 1_100, TransactionType.LOAN_OUT),
                installment(3, 2, LocalDate.of(2026, 11, 1), 1_000, TransactionType.LOAN_OUT),
                installment(4, 2, LocalDate.of(2026, 11, 3), 250, TransactionType.LOAN_IN),
            ),
            mapOf(1L to borrowed, 2L to lent),
        )
        assertEquals(10_000L, flows.received)
        assertEquals(1_100L, flows.paid)
        assertEquals(1_000L, flows.lent)
        assertEquals(250L, flows.collected)
    }

    @Test
    fun `loan flows carry what is still outstanding apart by direction`() {
        val borrowed = loan(1, com.boxdev.boxwallet.domain.model.LoanType.EXPENSE, 10_000, paid = 1_100, remainingMain = 8_900)
        val lent = loan(2, com.boxdev.boxwallet.domain.model.LoanType.INCOME, 1_000, paid = 1_000, remainingMain = 0)
        val flows = loanFlows(emptyList(), mapOf(1L to borrowed, 2L to lent))
        assertEquals(8_900L, flows.owe)
        assertEquals(0L, flows.owed)
    }

    @Test
    fun `loan flows ignore movements whose loan is missing`() {
        val flows = loanFlows(
            listOf(installment(1, 99, LocalDate.of(2026, 11, 1), 500, TransactionType.LOAN_IN)),
            emptyMap(),
        )
        assertEquals(LoanFlows(0, 0, 0, 0, 0, 0), flows)
    }

    @Test
    fun `loan movements stay out of income and expense totals`() {
        val month = java.time.YearMonth.of(2026, 11)
        val base = listOf(
            installment(1, 1, LocalDate.of(2026, 11, 2), 1_000, TransactionType.INCOME),
            installment(2, 1, LocalDate.of(2026, 11, 3), 300, TransactionType.EXPENSE),
        )
        val withLoans = base + listOf(
            installment(3, 5, LocalDate.of(2026, 11, 4), 10_000, TransactionType.LOAN_IN),
            installment(4, 5, LocalDate.of(2026, 11, 5), 1_100, TransactionType.LOAN_OUT),
        )
        assertEquals(base.monthlyTotals(listOf(month)), withLoans.monthlyTotals(listOf(month)))
        assertEquals(base.net(), withLoans.net())
    }

    // bestColumnSplit

    @Test
    fun `single section stays in the left column`() {
        assertEquals(0, bestColumnSplit(listOf(500), gap = 16))
    }

    @Test
    fun `split picks the arrangement whose columns end level`() {
        // 400 + 300 on the left and 100 + 600 on the right both total 700; no other split is level.
        val mask = bestColumnSplit(listOf(400, 300, 100, 600), gap = 0)
        assertEquals(0b110, mask)
    }

    @Test
    fun `gaps between stacked sections count toward a column`() {
        // With a 50 gap, 100 + 100 stacked is 250 against 200 alone: off by 50.
        // Every other split is off by 250 or more.
        assertEquals(0b10, bestColumnSplit(listOf(100, 100, 200), gap = 50))
    }
}
