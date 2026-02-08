package com.boxdev.boxwallet.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.boxdev.boxwallet.data.datastore.UserPreferences

@Composable
fun rememberCurrencyFormatter(): CurrencyFormatter {
    val context = LocalContext.current
    val userPreferences = UserPreferences(context)
    val currencySymbol by userPreferences.currencySymbol.collectAsState(initial = "$")

    return CurrencyFormatter(currencySymbol)
}

class CurrencyFormatter(private val symbol: String = "$") {
    fun format(amount: Double): String {
        return "$symbol%.2f".format(amount)
    }

    fun formatWithoutSymbol(amount: Double): String {
        return "%.2f".format(amount)
    }
}
