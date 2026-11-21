package com.boxdev.boxwallet.util

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CurrentDayTest {

    @Test
    fun emitsTodayThenEachMidnight() = runTest {
        val start = LocalDateTime.of(2026, 11, 21, 23, 58, 30)
        val days = dayTicks { start.plusNanos(currentTime * 1_000_000) }.take(3).toList()

        assertEquals(
            listOf(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 23)),
            days,
        )
        // 90 s to the first midnight, then a full day.
        assertEquals(90_000L + 86_400_000L, currentTime)
    }
}
