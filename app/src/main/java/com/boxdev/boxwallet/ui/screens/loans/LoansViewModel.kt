package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Open loans by direction, newest first, and the settled ones apart so they go last. */
data class LoanGroups(
    /** Loans I lent ([LoanType.INCOME]) that are not settled. */
    val owedToMe: List<Loan> = emptyList(),
    /** Loans I borrowed ([LoanType.EXPENSE]) that are not settled. */
    val iOwe: List<Loan> = emptyList(),
    val settled: List<Loan> = emptyList(),
) {
    val isEmpty: Boolean get() = owedToMe.isEmpty() && iOwe.isEmpty() && settled.isEmpty()
}

fun List<Loan>.groupForList(): LoanGroups {
    val (settled, open) = partition { it.isSettled }
    val newestFirst = compareByDescending<Loan> { it.date }
    return LoanGroups(
        owedToMe = open.filter { it.loanType == LoanType.INCOME }.sortedWith(newestFirst),
        iOwe = open.filter { it.loanType == LoanType.EXPENSE }.sortedWith(newestFirst),
        settled = settled.sortedWith(newestFirst),
    )
}

data class LoansUiState(
    val groups: LoanGroups = LoanGroups(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class LoansViewModel @Inject constructor(
    loanRepository: LoanRepository,
) : ViewModel() {

    val uiState: StateFlow<LoansUiState> = loanRepository.observeAll()
        .map { LoansUiState(groups = it.groupForList(), isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LoansUiState(),
        )
}
