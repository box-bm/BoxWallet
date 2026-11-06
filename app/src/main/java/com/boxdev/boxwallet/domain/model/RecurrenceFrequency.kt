package com.boxdev.boxwallet.domain.model

import androidx.annotation.StringRes
import com.boxdev.boxwallet.R
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal

enum class RecurrenceFrequency(@StringRes val displayNameRes: Int) {
    DAILY(R.string.frequency_daily),
    WEEKLY(R.string.frequency_weekly),
    BIWEEKLY(R.string.frequency_biweekly),
    MONTHLY(R.string.frequency_monthly),
    YEARLY(R.string.frequency_yearly),
    CUSTOM_MONTHS(R.string.frequency_custom);

    /** Moves [date] forward by [steps] periods of this frequency. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Temporal> advance(date: T, interval: Int, steps: Long = 1): T = when (this) {
        DAILY -> date.plus(steps * interval, ChronoUnit.DAYS)
        WEEKLY -> date.plus(steps * interval, ChronoUnit.WEEKS)
        BIWEEKLY -> date.plus(steps * 2, ChronoUnit.WEEKS)
        MONTHLY -> date.plus(steps, ChronoUnit.MONTHS)
        YEARLY -> date.plus(steps, ChronoUnit.YEARS)
        CUSTOM_MONTHS -> date.plus(steps * interval, ChronoUnit.MONTHS)
    } as T
}


