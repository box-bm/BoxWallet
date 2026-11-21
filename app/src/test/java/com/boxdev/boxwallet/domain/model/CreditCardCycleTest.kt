package com.boxdev.boxwallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CreditCardCycleTest {

    private val today = LocalDate.of(2026, 11, 21)

    private fun out(day: Int, minor: Long) = CardMovement(LocalDate.of(2026, 11, day), -minor, isInflow = false)
    private fun inn(day: Int, minor: Long) = CardMovement(LocalDate.of(2026, 11, day), minor, isInflow = true)

    @Test
    fun lastClosingSameMonth() = assertEquals(LocalDate.of(2026, 11, 15), lastClosingDate(15, today))

    @Test
    fun lastClosingPreviousMonth() = assertEquals(LocalDate.of(2026, 10, 25), lastClosingDate(25, today))

    @Test
    fun lastClosingClampsFebruary() {
        assertEquals(LocalDate.of(2027, 2, 28), lastClosingDate(31, LocalDate.of(2027, 3, 10)))
        assertEquals(LocalDate.of(2028, 2, 29), lastClosingDate(29, LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun lastClosingAcrossYear() = assertEquals(LocalDate.of(2026, 12, 28), lastClosingDate(28, LocalDate.of(2027, 1, 5)))

    @Test
    fun dueNextMonthWhenDueDayNotAfterClosing() =
        assertEquals(LocalDate.of(2026, 12, 5), dueDateAfter(LocalDate.of(2026, 11, 15), 5))

    @Test
    fun dueSameMonthClamped() = assertEquals(LocalDate.of(2026, 11, 30), dueDateAfter(LocalDate.of(2026, 11, 10), 31))

    @Test
    fun dueAcrossYear() = assertEquals(LocalDate.of(2027, 1, 10), dueDateAfter(LocalDate.of(2026, 12, 28), 10))

    @Test
    fun toPayIsStatementMinusPaymentsSince() {
        val status = statementStatus(listOf(out(10, 50000), out(18, 30000), inn(19, 20000)), 15, 5, today)

        assertEquals(50000L, status.statementDebtMinor)
        assertEquals(20000L, status.paidSinceMinor)
        assertEquals(30000L, status.toPayMinor)
        assertFalse(status.noStatementYet)
    }

    @Test
    fun overpaidStatementIsZero() {
        val status = statementStatus(listOf(out(10, 50000), inn(19, 60000)), 15, 5, today)

        assertEquals(0L, status.toPayMinor)
    }

    @Test
    fun noStatementYetWhenNothingBeforeClosing() {
        val status = statementStatus(listOf(out(18, 30000)), 15, 5, today)

        assertTrue(status.noStatementYet)
        assertEquals(0L, status.toPayMinor)
    }
}
