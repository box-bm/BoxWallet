package com.boxdev.boxwallet.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

val accountIconOptions: List<Pair<String, ImageVector>> = listOf(
    "AccountBalance" to Icons.Default.AccountBalance,
    "AccountBalanceWallet" to Icons.Default.AccountBalanceWallet,
    "CreditCard" to Icons.Default.CreditCard,
    "Savings" to Icons.Default.Savings,
    "Payments" to Icons.Default.Payments,
    "Wallet" to Icons.Default.Wallet,
    "LocalAtm" to Icons.Default.LocalAtm,
    "Euro" to Icons.Default.Euro,
    "AttachMoney" to Icons.Default.AttachMoney,
    "CurrencyExchange" to Icons.Default.CurrencyExchange,
    "Work" to Icons.Default.Work,
    "Home" to Icons.Default.Home,
    "Flight" to Icons.Default.Flight,
    "School" to Icons.Default.School,
    "Store" to Icons.Default.Store,
    "Smartphone" to Icons.Default.Smartphone,
    "DirectionsCar" to Icons.Default.DirectionsCar,
    "ShoppingCart" to Icons.Default.ShoppingCart,
)

private val accountIconsByKey = accountIconOptions.toMap()

fun accountIcon(key: String): ImageVector =
    accountIconsByKey[key] ?: Icons.Default.AccountBalance
