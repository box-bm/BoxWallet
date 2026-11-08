package com.boxdev.boxwallet.domain.money

import java.text.Collator
import java.util.Currency
import java.util.Locale

data class Country(val code: String, val name: String, val currency: Currency)

object CurrencyCatalog {

    private val PREFERRED_SYMBOLS = mapOf(
        "USD" to "$",
        "GTQ" to "Q",
        "HNL" to "L",
        "NIO" to "C$",
        "CRC" to "₡",
        "PAB" to "B/.",
        "BZD" to "BZ$",
        "SVC" to "₡",
        "MXN" to "MX$",
        "EUR" to "€",
    )

    /** All selectable currencies (ISO 4217 "X**" pseudo/metal codes excluded), sorted by display name. */
    fun all(locale: Locale): List<Currency> {
        val collator = Collator.getInstance(locale)
        return Currency.getAvailableCurrencies()
            .filter { !it.currencyCode.startsWith("X") }
            .sortedWith(compareBy(collator) { displayName(it, locale) })
    }

    fun symbol(c: Currency, locale: Locale): String =
        PREFERRED_SYMBOLS[c.currencyCode] ?: c.getSymbol(locale)

    fun displayName(c: Currency, locale: Locale): String = c.getDisplayName(locale)

    fun forCountry(countryCode: String): Currency? {
        val code = countryCode.uppercase(Locale.ROOT)
        if (code !in Locale.getISOCountries()) return null
        // El Salvador adopted USD in 2001; some JDK data still reports the retired SVC.
        if (code == "SV") return Currency.getInstance("USD")
        return try {
            Currency.getInstance(Locale("", code))
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun countries(locale: Locale): List<Country> {
        val collator = Collator.getInstance(locale)
        return Locale.getISOCountries().mapNotNull { code ->
            val currency = forCountry(code)?.takeIf { isSupported(it.currencyCode) } ?: return@mapNotNull null
            Country(code, Locale("", code).getDisplayCountry(locale), currency)
        }.sortedWith(compareBy(collator) { it.name })
    }

    fun isSupported(code: String): Boolean {
        if (code.startsWith("X")) return false
        return try {
            Currency.getInstance(code)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
