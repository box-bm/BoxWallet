package com.boxdev.boxwallet.domain.model

enum class ReminderDuration(val label: String, val secondsBefore: Long, val debugOnly: Boolean = false) {
    AT_THE_MOMENT("At the moment", 0),
    ONE_MINUTE("1 minute", 60, debugOnly = true),
    FIVE_MINUTES("5 minutes", 300),
    FIFTEEN_MINUTES("15 minutes", 900),
    THIRTY_MINUTES("30 minutes", 1800),
    ONE_HOUR("1 hour", 3600),
    TWO_HOURS("2 hours", 7200),
    SIX_HOURS("6 hours", 21600),
    ONE_DAY("1 day", 86400);

    companion object {
        fun fromSeconds(seconds: Long?): ReminderDuration? {
            if (seconds == null) return null
            return entries.find { it.secondsBefore == seconds }
        }
    }
}
