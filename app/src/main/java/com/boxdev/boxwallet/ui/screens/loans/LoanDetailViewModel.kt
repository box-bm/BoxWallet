package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoanDetailUiState(
    val loan: Loan? = null,
    val installments: List<Transaction> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class LoanDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val loanRepository: LoanRepository,
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val loanId: Long = checkNotNull(savedStateHandle["loanId"])

    val uiState: StateFlow<LoanDetailUiState> = combine(
        loanRepository.getAllLoans(),
        transactionRepository.getInstallmentsByLoanId(loanId)
    ) { loans, installments ->
        LoanDetailUiState(
            loan = loans.find { it.id == loanId },
            installments = installments,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoanDetailUiState()
    )

    fun toggleInstallmentCleared(transaction: Transaction) {
        viewModelScope.launch {
            transactionRepository.updateTransaction(transaction.copy(isCleared = !transaction.isCleared))
        }
    }
}
