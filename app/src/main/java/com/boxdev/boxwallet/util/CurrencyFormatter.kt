package com.boxdev.boxwallet.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.boxdev.boxwallet.domain.money.CurrencyCatalog
import com.boxdev.boxwallet.domain.money.Money
import com.boxdev.boxwallet.domain.money.minorDigits
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale
import kotlin.math.roundToLong

/** Provided once at the root from [com.boxdev.boxwallet.data.datastore.UserPreferences.mainCurrency]. */
val LocalCurrencyFormatter = staticCompositionLocalOf { CurrencyFormatter() }

@Composable
@ReadOnlyComposable
fun rememberCurrencyFormatter(): CurrencyFormatter = LocalCurrencyFormatter.current

/**
 * Formats [Money] as `symbol` + grouped amount with the currency's own decimals, e.g. `Q1,830.35`.
 * Separators are always US-style; [locale] only selects the currency symbol.
 * Negative values put the minus (U+2212) before the symbol: `−$12.00`.
 */
class CurrencyFormatter(
    val main: Currency = Currency.getInstance("USD"),
    private val locale: Locale = Locale.getDefault(),
) {
    /** Symbol of [main]; used by amount inputs until they become currency-aware. */
    val symbol: String get() = CurrencyCatalog.symbol(main, locale)

    fun format(money: Money): String {
        val digits = money.currency.minorDigits()
        val pattern = if (digits == 0) "#,##0" else "#,##0." + "0".repeat(digits)
        val number = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
        val body = CurrencyCatalog.symbol(money.currency, locale) + number.format(money.toMajor().abs())
        return if (money.minor < 0) "−$body" else body
    }

    /**
     * The original amount plus, when it is in a foreign currency, its estimated equivalent in [main]
     * (`"≈ Q165.79"`); the second value is null when [original] is already in [main].
     */
    fun withEquivalent(original: Money, inMain: Money): Pair<String, String?> =
        format(original) to if (original.currency == main) null else "≈ " + format(inMain)

    fun formatMain(minor: Long): String = format(Money(minor, main))

    /** Short form for chart axes: `Q950`, `Q1.2k`, `Q12k`, `Q3.4M`. */
    fun formatCompact(money: Money): String = compactOf(money.toMajor(), money.currency)

    fun formatMainCompact(minor: Long): String = formatCompact(Money(minor, main))

    private fun compactOf(major: BigDecimal, currency: Currency): String {
        val abs = major.abs().toDouble()
        val body = when {
            abs >= 1_000_000 -> compact(abs / 1_000_000) + "M"
            abs >= 1_000 -> compact(abs / 1_000) + "k"
            else -> abs.roundToLong().toString()
        }
        return (if (major.signum() < 0) "−" else "") + CurrencyCatalog.symbol(currency, locale) + body
    }

    private fun compact(v: Double): String =
        if (v >= 10 || v % 1.0 < 0.05) v.roundToLong().toString() else String.format(Locale.US, "%.1f", v)
}
