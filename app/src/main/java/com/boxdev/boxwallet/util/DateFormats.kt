package com.boxdev.boxwallet.util

import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Shared localized formatters, built once per locale instead of per row or recomposition.
 * Keyed by the current default locale, so a language change picks up new formatters.
 */
object DateFormats {
    private val cache = ConcurrentHashMap<Pair<Locale, String>, DateTimeFormatter>()

    private fun cached(key: String, build: (Locale) -> DateTimeFormatter): DateTimeFormatter {
        val locale = Locale.getDefault()
        return cache.getOrPut(locale to key) { build(locale) }
    }

    /** "Nov 6, 2026" style date. */
    val mediumDate: DateTimeFormatter
        get() = cached("mediumDate") { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(it) }

    /** "3:45 PM" style time. */
    val shortTime: DateTimeFormatter
        get() = cached("shortTime") { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(it) }

    /** Locale-correct short day ("6 nov" / "Nov 6"), with the year when [withYear]. */
    fun shortDay(withYear: Boolean): DateTimeFormatter {
        val skeleton = if (withYear) "yMMMd" else "MMMd"
        return cached(skeleton) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(it, skeleton), it) }
    }
}
