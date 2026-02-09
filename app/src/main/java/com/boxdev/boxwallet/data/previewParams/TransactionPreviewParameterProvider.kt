package com.boxdev.boxwallet.data.previewParams

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Transaction
import java.time.LocalDate

class TransactionPreviewParameterProvider : PreviewParameterProvider<Transaction> {
    override val values = sequenceOf(
        Transaction(
            id = 1,
            amount = 2000.0,
            currency = "Q",
            description = "Transaction description",
            date = LocalDate.of(2026, 1, 1),
            type = TransactionType.INCOME,
            isCleared = true,
            hasReminder = false,
            reminderDate = null,
            categoryId = null,
            loanId = null,
            installmentNumber = null
        ),
        Transaction(
            id = 2,
            amount = 2000.0,
            currency = "Q",
            description = "expense",
            date = LocalDate.of(2026, 1, 1),
            type = TransactionType.EXPENSE,
            isCleared = true,
            hasReminder = false,
            reminderDate = null,
            categoryId = null,
            loanId = null,
            installmentNumber = null
        ),
        Transaction(
            id = 3,
            amount = 2000.0,
            currency = "Q",
            description = "expense",
            date = LocalDate.of(2026, 1, 1),
            type = TransactionType.EXPENSE,
            isCleared = true,
            hasReminder = true,
            reminderDate = LocalDate.of(2026, 2, 1),
            categoryId = null,
            loanId = null,
            installmentNumber = null
        )
    )
}