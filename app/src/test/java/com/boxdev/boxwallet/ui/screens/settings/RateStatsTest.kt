package com.boxdev.boxwallet.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RateStatsTest {
    private fun series(vararg v: String) = v.mapIndexed { i, s -> LocalDate.of(2026, 1, 1 + i) to BigDecimal(s) }

    @Test
    fun emptySeriesHasNoStats() {
        assertNull(rateStats(emptyList()))
    }

    @Test
    fun computesMinMaxCurrentAndDailyChange() {
        val stats = rateStats(series("7.60", "7.70", "7.64"))!!
        assertEquals(BigDecimal("7.60"), stats.min)
        assertEquals(BigDecimal("7.70"), stats.max)
        assertEquals(BigDecimal("7.64"), stats.current)
        assertEquals(BigDecimal("-0.78"), stats.dailyChangePercent)
    }

    @Test
    fun singlePointHasNoDailyChange() {
        val stats = rateStats(series("7.60"))!!
        assertNull(stats.dailyChangePercent)
        assertEquals(stats.current, stats.min)
    }

    @Test
    fun unsortedInputUsesNewestDayAsCurrent() {
        val s = series("7.60", "7.70", "7.64").reversed()
        assertEquals(BigDecimal("7.64"), rateStats(s)!!.current)
    }

    @Test
    fun zeroPreviousRateGivesNoChange() {
        assertNull(rateStats(series("0", "1"))!!.dailyChangePercent)
    }
}
