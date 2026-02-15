package com.boxdev.boxwallet.domain.model

import androidx.annotation.StringRes
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.TransactionType

enum class LoanType(@StringRes val displayNameRes: Int) {
    EXPENSE(R.string.loan_type_expense),
    INCOME(R.string.loan_type_income);

    fun toTransactionType(): TransactionType = when (this) {
        EXPENSE -> TransactionType.EXPENSE
        INCOME -> TransactionType.INCOME
    }
}
