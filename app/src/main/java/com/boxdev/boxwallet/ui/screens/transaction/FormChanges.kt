package com.boxdev.boxwallet.ui.screens.transaction

import com.boxdev.boxwallet.ui.screens.loans.LoanFormState

/** True when a user-editable field differs from [initial]; loading, error and derived fields are ignored. */
fun TransactionFormState.differsFrom(initial: TransactionFormState): Boolean =
    amount != initial.amount ||
        accountId != initial.accountId ||
        currency != initial.currency ||
        manualRate != initial.manualRate ||
        equivalent != initial.equivalent ||
        description != initial.description ||
        categoryId != initial.categoryId ||
        date != initial.date ||
        type != initial.type ||
        isCleared != initial.isCleared ||
        hasReminder != initial.hasReminder ||
        reminderDuration != initial.reminderDuration ||
        isRecurrent != initial.isRecurrent ||
        recurrenceFrequency != initial.recurrenceFrequency ||
        recurrenceInterval != initial.recurrenceInterval ||
        inInstallments != initial.inInstallments ||
        installmentCount != initial.installmentCount

/** True when a user-editable field differs from [initial]; loading, error and derived fields are ignored. */
fun LoanFormState.differsFrom(initial: LoanFormState): Boolean =
    name != initial.name ||
        description != initial.description ||
        totalAmount != initial.totalAmount ||
        disbursedAmount != initial.disbursedAmount ||
        loanType != initial.loanType ||
        accountId != initial.accountId ||
        date != initial.date ||
        hasPlan != initial.hasPlan ||
        (hasPlan && (
            installments != initial.installments ||
                frequency != initial.frequency ||
                frequencyInterval != initial.frequencyInterval ||
                firstDueDate != initial.firstDueDate)) ||
        (!hasPlan && expectedDate != initial.expectedDate)
