package com.boxdev.boxwallet.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Provided once at the root from [com.boxdev.boxwallet.data.datastore.UserPreferences.currencySymbol]. */
val LocalCurrencyFormatter = staticCompositionLocalOf { CurrencyFormatter() }

@Composable
@ReadOnlyComposable
fun rememberCurrencyFormatter(): CurrencyFormatter = LocalCurrencyFormatter.current

/**
 * Formats money as `symbol` + grouped amount with two decimals, e.g. `Q1,830.35`.
 * Grouping uses US-style separators, which is the convention across Central America.
 * Negative values put the minus before the symbol: `−$12.00`.
 */
class CurrencyFormatter(val symbol: String = "$") {
    private val number = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))

    fun format(amount: Double): String {
        val body = symbol + number.format(kotlin.math.abs(amount))
        return if (amount < 0 && number.format(kotlin.math.abs(amount)) != "0.00") "−$body" else body
    }
}
