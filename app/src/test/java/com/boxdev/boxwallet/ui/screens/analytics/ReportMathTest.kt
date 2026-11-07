package com.boxdev.boxwallet.ui.screens.analytics

import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
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

    // installmentState

    @Test
    fun `paid installment is paid even when its date has passed`() {
        assertEquals(InstallmentState.PAID, installmentState(true, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 6)))
    }

    @Test
    fun `unpaid installment past its date is overdue`() {
        assertEquals(InstallmentState.OVERDUE, installmentState(false, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 6)))
    }

    @Test
    fun `unpaid installment due today is still open`() {
        assertEquals(InstallmentState.OPEN, installmentState(false, LocalDate.of(2026, 11, 6), LocalDate.of(2026, 11, 6)))
    }

    // summarizeInstallments

    private val today = LocalDate.of(2026, 11, 6)

    private fun installment(
        id: Long,
        loanId: Long,
        day: LocalDate,
        amount: Double,
        type: TransactionType = TransactionType.EXPENSE,
        paid: Boolean = false,
    ) = Transaction(
        id = id,
        amount = amount,
        date = day.atTime(9, 0),
        type = type,
        isCleared = paid,
        loanId = loanId,
    )

    @Test
    fun `no installments gives no summary`() {
        assertNull(summarizeInstallments(emptyList(), emptyMap(), today))
    }

    @Test
    fun `money to pay and to collect are never added together`() {
        val summary = summarizeInstallments(
            listOf(
                installment(1, loanId = 10, day = novStart, amount = 300.0),
                installment(2, loanId = 20, day = novStart, amount = 150.0, type = TransactionType.INCOME),
            ),
            mapOf(10L to "Moto", 20L to "Carlos"),
            today,
        )!!
        assertEquals(300.0, summary.toPayDue, 0.001)
        assertEquals(150.0, summary.toCollectDue, 0.001)
    }

    @Test
    fun `each installment keeps its own state in due-date order`() {
        val summary = summarizeInstallments(
            listOf(
                installment(1, loanId = 10, day = LocalDate.of(2026, 10, 1), amount = 100.0, paid = true),
                installment(2, loanId = 10, day = LocalDate.of(2026, 11, 1), amount = 100.0),
                installment(3, loanId = 10, day = LocalDate.of(2026, 11, 20), amount = 100.0),
            ),
            mapOf(10L to "Moto"),
            today,
        )!!
        assertEquals(
            listOf(InstallmentState.PAID, InstallmentState.OVERDUE, InstallmentState.OPEN),
            summary.states,
        )
        assertEquals(1, summary.paidCount)
        assertEquals(1, summary.overdueCount)
        assertEquals(200.0, summary.toPayOpen, 0.001)
    }

    @Test
    fun `unpaid installments are grouped once per loan, overdue loans first`() {
        val summary = summarizeInstallments(
            listOf(
                installment(1, loanId = 10, day = LocalDate.of(2026, 11, 25), amount = 300.0),
                installment(2, loanId = 20, day = LocalDate.of(2026, 10, 1), amount = 150.0, type = TransactionType.INCOME),
                installment(3, loanId = 20, day = LocalDate.of(2026, 11, 1), amount = 150.0, type = TransactionType.INCOME),
                installment(4, loanId = 20, day = LocalDate.of(2026, 11, 28), amount = 150.0, type = TransactionType.INCOME),
            ),
            mapOf(10L to "Moto", 20L to "Carlos"),
            today,
        )!!
        assertEquals(listOf("Carlos", "Moto"), summary.pending.map { it.loanName })
        val carlos = summary.pending.first()
        assertEquals(3, carlos.count)
        assertEquals(2, carlos.overdueCount)
        assertEquals(450.0, carlos.amount, 0.001)
        assertEquals(LocalDate.of(2026, 10, 1), carlos.firstDue)
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
