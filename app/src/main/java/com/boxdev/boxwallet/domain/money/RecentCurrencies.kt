package com.boxdev.boxwallet.domain.money

import com.boxdev.boxwallet.data.local.dao.CurrencyUsage
import java.text.Normalizer
import java.util.Currency
import java.util.Locale

/** What the currency picker shows: recently used first (empty while searching), then the rest. */
data class PickerSections(val recent: List<Currency>, val all: List<Currency>)

private const val MAX_RECENT = 5

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Lowercases and strips accents so searches match regardless of either. */
fun foldForSearch(text: String, locale: Locale): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(locale)

/**
 * Builds the picker lists. [recent] arrives ordered by use and recency and keeps that order.
 * A non-blank [query] matches code, localized name or symbol, ignoring case and accents, and hides
 * the recent section (matches stay in [PickerSections.all]).
 */
fun pickerSections(all: List<Currency>, recent: List<CurrencyUsage>, query: String, locale: Locale): PickerSections {
    val q = foldForSearch(query.trim(), locale)
    if (q.isEmpty()) {
        val known = all.associateBy { it.currencyCode }
        val recentCurrencies = recent.mapNotNull { known[it.currencyCode] }.distinct().take(MAX_RECENT)
        val recentCodes = recentCurrencies.map { it.currencyCode }.toSet()
        return PickerSections(recentCurrencies, all.filter { it.currencyCode !in recentCodes })
    }
    val matches = all.filter {
        foldForSearch(it.currencyCode, locale).contains(q) ||
            foldForSearch(CurrencyCatalog.displayName(it, locale), locale).contains(q) ||
            foldForSearch(CurrencyCatalog.symbol(it, locale), locale).contains(q) ||
            foldForSearch(it.getSymbol(locale), locale).contains(q)
    }
    return PickerSections(emptyList(), matches)
}
