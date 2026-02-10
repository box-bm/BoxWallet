package com.boxdev.boxwallet.domain.model

enum class RecurrenceFrequency {
    DAILY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    YEARLY,
    CUSTOM_MONTHS;

    fun getDisplayName(): String = when (this) {
        DAILY -> "Daily"
        WEEKLY -> "Weekly"
        BIWEEKLY -> "Biweekly"
        MONTHLY -> "Monthly"
        YEARLY -> "Yearly"
        CUSTOM_MONTHS -> "Custom"
    }
}
