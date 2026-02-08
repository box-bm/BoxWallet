package com.boxdev.boxwallet.domain.model

enum class ReminderDuration(val label: String, val minutesBefore: Long?) {
    FIFTEEN_MINUTES("15 minutes", 15),
    THIRTY_MINUTES("30 minutes", 30),
    ONE_HOUR("1 hour", 60),
    ONE_DAY("1 day", 1440),
    ONE_WEEK("1 week", 10080),
    CUSTOM("Custom date", null);

    companion object {
        fun fromMinutes(minutes: Long?): ReminderDuration {
            if (minutes == null) return CUSTOM
            return entries.find { it.minutesBefore == minutes } ?: CUSTOM
        }
    }
}
