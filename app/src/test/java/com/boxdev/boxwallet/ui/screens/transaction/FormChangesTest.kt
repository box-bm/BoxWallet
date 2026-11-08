package com.boxdev.boxwallet.ui.screens.transaction

import com.boxdev.boxwallet.ui.screens.loans.LoanFormState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Currency

class FormChangesTest {

    private val eur = Currency.getInstance("EUR")

    @Test
    fun identicalTransactionStatesDoNotDiffer() {
        val s = TransactionFormState(amount = "5")
        assertFalse(s.differsFrom(s.copy()))
    }

    @Test
    fun editedAmountDiffers() {
        val initial = TransactionFormState(amount = "5")
        assertTrue(initial.copy(amount = "6").differsFrom(initial))
    }

    @Test
    fun editedOtherTransactionFieldsDiffer() {
        val initial = TransactionFormState()
        assertTrue(initial.copy(description = "x").differsFrom(initial))
        assertTrue(initial.copy(currency = eur).differsFrom(initial))
        assertTrue(initial.copy(isCleared = true).differsFrom(initial))
        assertTrue(initial.copy(recurrenceInterval = "2").differsFrom(initial))
    }

    @Test
    fun transactionBookkeepingFieldsAreIgnored() {
        val initial = TransactionFormState(amount = "5")
        assertFalse(initial.copy(isLoading = true).differsFrom(initial))
        assertFalse(initial.copy(isSaved = true).differsFrom(initial))
        assertFalse(initial.copy(error = TransactionFormError.SaveFailed).differsFrom(initial))
        assertFalse(initial.copy(needsManualRate = true).differsFrom(initial))
        assertFalse(initial.copy(accountCurrency = eur).differsFrom(initial))
        assertFalse(initial.copy(wasCleared = true).differsFrom(initial))
    }

    @Test
    fun revertingATransactionEditDoesNotDiffer() {
        val initial = TransactionFormState(amount = "5")
        assertFalse(initial.copy(amount = "6").copy(amount = "5").differsFrom(initial))
    }

    @Test
    fun identicalLoanStatesDoNotDiffer() {
        val s = LoanFormState(name = "Car")
        assertFalse(s.differsFrom(s.copy()))
    }

    @Test
    fun editedLoanNameDiffers() {
        val initial = LoanFormState(name = "Car")
        assertTrue(initial.copy(name = "Bike").differsFrom(initial))
        assertTrue(initial.copy(installments = "3").differsFrom(initial))
    }

    @Test
    fun loanBookkeepingFieldsAreIgnored() {
        val initial = LoanFormState(name = "Car")
        assertFalse(initial.copy(isLoading = true).differsFrom(initial))
        assertFalse(initial.copy(error = "boom").differsFrom(initial))
        assertFalse(initial.copy(paidInstallments = 2).differsFrom(initial))
        assertFalse(initial.copy(isSaved = true).differsFrom(initial))
    }

    @Test
    fun revertingALoanEditDoesNotDiffer() {
        val initial = LoanFormState(name = "Car")
        assertFalse(initial.copy(name = "Bike").copy(name = "Car").differsFrom(initial))
    }
}
