package com.boxdev.boxwallet.util

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
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

    @Test
    fun oneTimerServesEveryCollector() = runTest {
        var timers = 0
        val today = LocalDate.of(2026, 11, 21)
        val ticks = flow {
            timers++
            emit(today)
            awaitCancellation()
        }
        val days = sharedDays(ticks, backgroundScope)
        val seen = mutableListOf<LocalDate>()

        repeat(3) { backgroundScope.launch { seen += days.first() } }
        backgroundScope.launch { days.collect {} }
        runCurrent()

        assertEquals(1, timers)
        assertEquals(listOf(today, today, today), seen)
    }

    @Test
    fun restartsAfterTheLastCollectorLeaves() = runTest {
        var timers = 0
        val ticks = flow {
            timers++
            emit(LocalDate.of(2026, 11, 21).plusDays(timers.toLong()))
            awaitCancellation()
        }
        val days = sharedDays(ticks, backgroundScope)

        val first = days.first()
        advanceTimeBy(10_000)
        val second = days.first()

        assertEquals(2, timers)
        assertEquals(LocalDate.of(2026, 11, 22), first)
        // The replayed day expired with the timer: no stale date first.
        assertEquals(LocalDate.of(2026, 11, 23), second)
    }
}
