package com.boxdev.boxwallet.domain.model

import androidx.annotation.StringRes
import com.boxdev.boxwallet.R

enum class RecurrenceFrequency(@StringRes val displayNameRes: Int) {
    DAILY(R.string.frequency_daily),
    WEEKLY(R.string.frequency_weekly),
    BIWEEKLY(R.string.frequency_biweekly),
    MONTHLY(R.string.frequency_monthly),
    YEARLY(R.string.frequency_yearly),
    CUSTOM_MONTHS(R.string.frequency_custom);
}


