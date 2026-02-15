package com.boxdev.boxwallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceFrequencyTest {

    @Test
    fun `DAILY display name`() {
        assertEquals("Daily", RecurrenceFrequency.DAILY.getDisplayName())
    }

    @Test
    fun `WEEKLY display name`() {
        assertEquals("Weekly", RecurrenceFrequency.WEEKLY.getDisplayName())
    }

    @Test
    fun `BIWEEKLY display name`() {
        assertEquals("Biweekly", RecurrenceFrequency.BIWEEKLY.getDisplayName())
    }

    @Test
    fun `MONTHLY display name`() {
        assertEquals("Monthly", RecurrenceFrequency.MONTHLY.getDisplayName())
    }

    @Test
    fun `YEARLY display name`() {
        assertEquals("Yearly", RecurrenceFrequency.YEARLY.getDisplayName())
    }

    @Test
    fun `CUSTOM_MONTHS display name`() {
        assertEquals("Custom", RecurrenceFrequency.CUSTOM_MONTHS.getDisplayName())
    }
}
