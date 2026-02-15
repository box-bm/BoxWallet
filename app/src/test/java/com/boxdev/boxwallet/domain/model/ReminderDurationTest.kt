package com.boxdev.boxwallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderDurationTest {

    @Test
    fun `fromMinutes returns FIFTEEN_MINUTES for 15`() {
        assertEquals(ReminderDuration.FIFTEEN_MINUTES, ReminderDuration.fromMinutes(15))
    }

    @Test
    fun `fromMinutes returns THIRTY_MINUTES for 30`() {
        assertEquals(ReminderDuration.THIRTY_MINUTES, ReminderDuration.fromMinutes(30))
    }

    @Test
    fun `fromMinutes returns ONE_HOUR for 60`() {
        assertEquals(ReminderDuration.ONE_HOUR, ReminderDuration.fromMinutes(60))
    }

    @Test
    fun `fromMinutes returns ONE_DAY for 1440`() {
        assertEquals(ReminderDuration.ONE_DAY, ReminderDuration.fromMinutes(1440))
    }

    @Test
    fun `fromMinutes returns ONE_WEEK for 10080`() {
        assertEquals(ReminderDuration.ONE_WEEK, ReminderDuration.fromMinutes(10080))
    }

    @Test
    fun `fromMinutes returns CUSTOM for null`() {
        assertEquals(ReminderDuration.CUSTOM, ReminderDuration.fromMinutes(null))
    }

    @Test
    fun `fromMinutes returns CUSTOM for unknown value`() {
        assertEquals(ReminderDuration.CUSTOM, ReminderDuration.fromMinutes(999))
    }
}
