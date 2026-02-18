package com.boxdev.boxwallet.data.previewParams

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.ui.screens.home.HomeViewModel

class HomeUiStatePreviewParameterProvider: PreviewParameterProvider<HomeViewModel> {
    override val values = sequenceOf(
        HomeViewModel(
            transactionRepository = TransactionRepository(
                transactionDao = TODO(),
                context = TODO()
            ),
            loanRepository = LoanRepository(
                loanDao = TODO(),
                transactionDao = TODO()
            ),
            categoryRepository = CategoryRepository(
               categoryDao = TODO()
            )
        )
    )
}
