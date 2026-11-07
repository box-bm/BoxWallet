package com.boxdev.boxwallet.domain.model

import androidx.annotation.StringRes
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.TransactionType

/**
 * Which way a loan's money goes. Labels say who owes whom ("I owe" / "Owed to me"), never
 * "expense" or "income", so the direction is unambiguous wherever a loan appears.
 */
enum class LoanType(
    @StringRes val displayNameRes: Int,
    @StringRes val remainingLabelRes: Int,
    @StringRes val settledLabelRes: Int,
) {
    EXPENSE(R.string.loan_type_expense, R.string.loan_left_to_pay, R.string.loan_total_paid),
    INCOME(R.string.loan_type_income, R.string.loan_left_to_collect, R.string.loan_total_collected);

    fun toTransactionType(): TransactionType = when (this) {
        EXPENSE -> TransactionType.EXPENSE
        INCOME -> TransactionType.INCOME
    }
}
