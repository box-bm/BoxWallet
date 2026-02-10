package com.boxdev.boxwallet.domain.model

import com.boxdev.boxwallet.data.local.entity.TransactionType

enum class LoanType {
    EXPENSE,
    INCOME;

    fun toTransactionType(): TransactionType = when (this) {
        EXPENSE -> TransactionType.EXPENSE
        INCOME -> TransactionType.INCOME
    }
}
