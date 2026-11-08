package com.boxdev.boxwallet.domain.model

/** All values are minor units of the main currency. */
data class FinancialSummary(
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val monthlyIncome: Long = 0,
    val monthlyExpense: Long = 0
) {
    val balance: Long get() = totalIncome - totalExpense
    val monthlyBalance: Long get() = monthlyIncome - monthlyExpense
}
