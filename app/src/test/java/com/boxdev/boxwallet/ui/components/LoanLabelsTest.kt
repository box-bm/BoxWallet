package com.boxdev.boxwallet.ui.components

import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.LoanType
import org.junit.Assert.assertEquals
import org.junit.Test

class LoanLabelsTest {

    @Test
    fun lendingDisbursementIsALoanToThem() {
        assertEquals(LoanMovementLabel.LENT_TO, loanMovementLabel(TransactionType.LOAN_OUT, LoanType.INCOME))
    }

    @Test
    fun borrowingDisbursementIsALoanFromThem() {
        assertEquals(LoanMovementLabel.BORROWED_FROM, loanMovementLabel(TransactionType.LOAN_IN, LoanType.EXPENSE))
    }

    @Test
    fun paymentOnWhatILentIsPaymentFromThem() {
        assertEquals(LoanMovementLabel.PAYMENT_FROM, loanMovementLabel(TransactionType.LOAN_IN, LoanType.INCOME))
    }

    @Test
    fun paymentOnWhatIBorrowedIsPaymentToThem() {
        assertEquals(LoanMovementLabel.PAYMENT_TO, loanMovementLabel(TransactionType.LOAN_OUT, LoanType.EXPENSE))
    }
}
