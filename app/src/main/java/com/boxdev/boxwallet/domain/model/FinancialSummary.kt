package com.boxdev.boxwallet.domain.model

data class FinancialSummary(
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpense: Double = 0.0,
    val balance: Double = 0.0,
    val monthlyBalance: Double = 0.0
)
