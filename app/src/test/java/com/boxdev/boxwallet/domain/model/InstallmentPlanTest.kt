package com.boxdev.boxwallet.domain.model

import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals

class InstallmentPlanTest {
    @Test
    fun `purchase on the closing day falls in that closing`() {
        assertEquals(
            listOf(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 15), LocalDate.of(2027, 1, 15)),
            installmentClosingDates(15, LocalDate.of(2026, 11, 15), 3),
        )
    }

    @Test
    fun `purchase the day after closing waits for the next one`() {
        assertEquals(LocalDate.of(2026, 12, 15), installmentClosingDates(15, LocalDate.of(2026, 11, 16), 3).first())
        assertEquals(LocalDate.of(2026, 12, 15), firstClosingOnOrAfter(15, LocalDate.of(2026, 11, 16)))
    }

    @Test
    fun `closing day 31 clamps in february`() {
        assertEquals(
            listOf(LocalDate.of(2027, 1, 31), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31)),
            installmentClosingDates(31, LocalDate.of(2027, 1, 31), 3),
        )
    }

    @Test
    fun `last installment absorbs the remainder`() {
        assertEquals(listOf(33333L, 33333L, 33334L), splitEvenly(100000, 3))
        assertEquals(listOf(100000L, 100000L, 100000L), splitEvenly(300000, 3))
    }
}
