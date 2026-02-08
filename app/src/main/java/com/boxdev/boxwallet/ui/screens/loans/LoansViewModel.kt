package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.domain.model.Loan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class LoansUiState(
    val loans: List<Loan> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class LoansViewModel @Inject constructor(
    loanRepository: LoanRepository
) : ViewModel() {

    val uiState: StateFlow<LoansUiState> = loanRepository.getAllLoans()
        .map { LoansUiState(loans = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LoansUiState()
        )
}
