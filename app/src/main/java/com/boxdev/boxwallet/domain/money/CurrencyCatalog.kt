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

    /**
     * ISO 4217 codes that are not spendable currencies: precious metals, IMF/settlement units,
     * bond-market units, test and "no currency" codes. Live "X" currencies (XOF, XAF, XCD, XPF,
     * XCG) are not here and stay selectable.
     */
    private val NON_CURRENCY_CODES = setOf(
        "XAU", "XAG", "XPT", "XPD", // metals
        "XDR", "XSU", "XUA", "XFO", "XFU", "XAD", // IMF / settlement / historical units
        "XBA", "XBB", "XBC", "XBD", // European bond-market units
        "XTS", "XXX", // testing / no currency
    )

    /**
     * ISO 4217 currencies in circulation (list one, minus fund/accounting codes such as BOV, CLF,
     * MXV, USN). The platform's `Currency.getAvailableCurrencies()` also returns historical codes
     * (AFA, ROL, CSD, MZM, ARL…), so the picker only offers codes from this list. ANG stays while
     * device data still reports it for Curaçao and Sint Maarten instead of its successor XCG.
     */
    internal val CIRCULATING_CODES = setOf(
        "AED", "AFN", "ALL", "AMD", "ANG", "AOA", "ARS", "AUD", "AWG", "AZN",
        "BAM", "BBD", "BDT", "BHD", "BIF", "BMD", "BND", "BOB", "BRL", "BSD", "BTN", "BWP", "BYN", "BZD",
        "CAD", "CDF", "CHF", "CLP", "CNY", "COP", "CRC", "CUP", "CVE", "CZK",
        "DJF", "DKK", "DOP", "DZD", "EGP", "ERN", "ETB", "EUR",
        "FJD", "FKP", "GBP", "GEL", "GHS", "GIP", "GMD", "GNF", "GTQ", "GYD",
        "HKD", "HNL", "HTG", "HUF", "IDR", "ILS", "INR", "IQD", "IRR", "ISK",
        "JMD", "JOD", "JPY", "KES", "KGS", "KHR", "KMF", "KPW", "KRW", "KWD", "KYD", "KZT",
        "LAK", "LBP", "LKR", "LRD", "LSL", "LYD",
        "MAD", "MDL", "MGA", "MKD", "MMK", "MNT", "MOP", "MRU", "MUR", "MVR", "MWK", "MXN", "MYR", "MZN",
        "NAD", "NGN", "NIO", "NOK", "NPR", "NZD", "OMR",
        "PAB", "PEN", "PGK", "PHP", "PKR", "PLN", "PYG", "QAR",
        "RON", "RSD", "RUB", "RWF",
        "SAR", "SBD", "SCR", "SDG", "SEK", "SGD", "SHP", "SLE", "SOS", "SRD", "SSP", "STN", "SVC", "SYP", "SZL",
        "THB", "TJS", "TMT", "TND", "TOP", "TRY", "TTD", "TWD", "TZS",
        "UAH", "UGX", "USD", "UYU", "UZS", "VED", "VES", "VND", "VUV", "WST",
        "XAF", "XCD", "XCG", "XOF", "XPF", "YER", "ZAR", "ZMW", "ZWG",
    )

    /**
     * Selectable currencies sorted by display name: those in circulation the platform knows, plus
     * any [keep] code (already used by stored data, or currently selected) even if it is retired,
     * so existing data never loses its currency in the picker.
     */
    fun all(locale: Locale, keep: Collection<String> = emptyList()): List<Currency> {
        val collator = Collator.getInstance(locale)
        val kept = keep.filter(::isSupported).toSet()
        return Currency.getAvailableCurrencies()
            .filter { it.currencyCode in CIRCULATING_CODES || it.currencyCode in kept }
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
        if (code in NON_CURRENCY_CODES) return false
        return try {
            Currency.getInstance(code)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
