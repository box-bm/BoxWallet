package com.boxdev.boxwallet.ui.components

import androidx.annotation.StringRes
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.Transaction

/** What a loan movement is, from its direction and whether the loan is lent or borrowed. */
enum class LoanMovementLabel(@StringRes val title: Int) {
    LENT_TO(R.string.loan_movement_lent_to),
    BORROWED_FROM(R.string.loan_movement_borrowed_from),
    PAYMENT_FROM(R.string.loan_movement_payment_from),
    PAYMENT_TO(R.string.loan_movement_payment_to),
}

/**
 * Money leaving a loan I lent is the loan itself ("Loan to"), coming back is a payment ("Payment from");
 * on a loan I borrowed it is the other way around.
 */
fun loanMovementLabel(type: TransactionType, loanType: LoanType): LoanMovementLabel = when (loanType) {
    LoanType.INCOME -> if (type == TransactionType.LOAN_OUT) LoanMovementLabel.LENT_TO else LoanMovementLabel.PAYMENT_FROM
    LoanType.EXPENSE -> if (type == TransactionType.LOAN_IN) LoanMovementLabel.BORROWED_FROM else LoanMovementLabel.PAYMENT_TO
}

/** The label of a loan movement once its loan's direction is known in [loanTypes]; null for any other row. */
fun loanMovementLabel(transaction: Transaction, loanTypes: Map<Long, LoanType>): LoanMovementLabel? =
    transaction.loanId?.let(loanTypes::get)?.let { loanMovementLabel(transaction.type, it) }
