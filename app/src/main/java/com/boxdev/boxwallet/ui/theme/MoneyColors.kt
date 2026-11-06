package com.boxdev.boxwallet.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic money roles. Material's scheme has no "income" or "expense" role, and spending is not
 * an error, so these live beside the [androidx.compose.material3.ColorScheme] instead of borrowing
 * `primary` / `error`.
 */
@Immutable
data class MoneyColors(
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
)

val LocalMoneyColors = staticCompositionLocalOf { lightMoneyColors }
