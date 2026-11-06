package com.boxdev.boxwallet.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Provided once at the root from [com.boxdev.boxwallet.data.datastore.UserPreferences.currencySymbol]. */
val LocalCurrencyFormatter = staticCompositionLocalOf { CurrencyFormatter() }

@Composable
@ReadOnlyComposable
fun rememberCurrencyFormatter(): CurrencyFormatter = LocalCurrencyFormatter.current

class CurrencyFormatter(val symbol: String = "$") {
    fun format(amount: Double): String {
        return "$symbol%.2f".format(amount)
    }
}
