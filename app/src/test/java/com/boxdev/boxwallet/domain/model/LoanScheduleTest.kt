package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Currency

class LoanScheduleTest {

    private val gtq = Currency.getInstance("GTQ")
    private fun q(minor: Long) = Money(minor, gtq)
    private val firstDue = LocalDate.of(2026, 2, 1)
    private fun plan(n: Int) = LoanPlan(n, RecurrenceFrequency.MONTHLY, 1, firstDue)
    private val today = LocalDate.of(2026, 1, 15)

    private fun schedule(total: Long = 110_000, n: Int = 10, paid: Long = 0, on: LocalDate = today) =
        allocate(q(total), plan(n), q(paid), on)

    @Test
    fun `exact payment covers one installment`() {
        val s = schedule(paid = 11_000)
        assertEquals(InstallmentState.PAID, s[0].state)
        assertEquals(InstallmentState.PENDING, s[1].state)
        assertEquals(q(0), s[1].covered)
    }

    @Test
    fun `larger payment spills into the next`() {
        val s = schedule(paid = 15_000)
        assertEquals(InstallmentState.PAID, s[0].state)
        assertEquals(InstallmentState.PARTIAL, s[1].state)
        assertEquals(q(4_000), s[1].covered)
        assertEquals(q(7_000), s[1].uncovered)
    }

    @Test
    fun `last installment absorbs the remainder`() {
        val s = schedule(total = 100_000, n = 3)
        assertEquals(listOf(q(33_333), q(33_333), q(33_334)), s.map { it.amount })
    }

    @Test
    fun `due dates follow the frequency from the first due date`() {
        val s = schedule(n = 3)
        assertEquals(listOf(1, 2, 3), s.map { it.number })
        assertEquals(
            listOf(firstDue, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1)),
            s.map { it.due },
        )
    }

    @Test
    fun `overdue only when due before today and not fully covered`() {
        val later = LocalDate.of(2026, 3, 15) // #1 (Feb 1) and #2 (Mar 1) are past
        val s = schedule(paid = 15_000, on = later)
        assertEquals(InstallmentState.PAID, s[0].state)
        assertEquals(InstallmentState.OVERDUE, s[1].state)
        assertEquals(InstallmentState.PENDING, s[2].state)
        // due today is not overdue yet
        assertEquals(InstallmentState.PENDING, schedule(on = firstDue)[0].state)
    }

    @Test
    fun `fully paid plan is all PAID`() {
        val s = schedule(paid = 110_000, on = LocalDate.of(2030, 1, 1))
        assertTrue(s.all { it.state == InstallmentState.PAID })
    }
}
