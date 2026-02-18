package com.boxdev.boxwallet.data.previewParams

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.domain.model.Transaction
import java.time.LocalDateTime

class TransactionPreviewParameterProvider : PreviewParameterProvider<Transaction> {
    override val values = sequenceOf(
        Transaction(
            id = 1,
            amount = 2000.0,
            currency = "Q",
            description = "Transaction description",
            date = LocalDateTime.of(2026, 1, 1, 10, 0),
            type = TransactionType.INCOME,
            isCleared = true,
            hasReminder = false,
            categoryId = null,
            loanId = null,
            installmentNumber = null
        ),
        Transaction(
            id = 2,
            amount = 2000.0,
            currency = "Q",
            description = "expense",
            date = LocalDateTime.of(2026, 1, 1, 14, 30),
            type = TransactionType.EXPENSE,
            isCleared = true,
            hasReminder = false,
            categoryId = null,
            loanId = null,
            installmentNumber = null
        ),
        Transaction(
            id = 3,
            amount = 2000.0,
            currency = "Q",
            description = "expense",
            date = LocalDateTime.of(2026, 1, 1, 9, 0),
            type = TransactionType.EXPENSE,
            isCleared = true,
            hasReminder = true,
            reminderSecondsBefore = 60,
            categoryId = null,
            loanId = null,
            installmentNumber = null
        )
    )
}
