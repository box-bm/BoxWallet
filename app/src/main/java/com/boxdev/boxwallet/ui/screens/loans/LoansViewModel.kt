package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.domain.model.Loan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class LoanStatusFilter { ALL, CURRENT, CLOSED }
enum class LoanSortBy { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }

data class LoansFilterState(
    val statusFilter: LoanStatusFilter = LoanStatusFilter.ALL,
    val sortBy: LoanSortBy = LoanSortBy.DATE_DESC
)

data class LoansUiState(
    val loans: List<Loan> = emptyList(),
    val filteredLoans: List<Loan> = emptyList(),
    val isLoading: Boolean = true,
    val filterState: LoansFilterState = LoansFilterState()
)

@HiltViewModel
class LoansViewModel @Inject constructor(
    loanRepository: LoanRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(LoansFilterState())

    val uiState: StateFlow<LoansUiState> = combine(
        loanRepository.getAllLoans(),
        _filterState
    ) { loans, filterState ->
        val filteredLoans = applyFilters(loans, filterState)
        LoansUiState(
            loans = loans,
            filteredLoans = filteredLoans,
            isLoading = false,
            filterState = filterState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoansUiState()
    )

    fun updateStatusFilter(status: LoanStatusFilter) {
        _filterState.value = _filterState.value.copy(statusFilter = status)
    }

    fun updateSortBy(sortBy: LoanSortBy) {
        _filterState.value = _filterState.value.copy(sortBy = sortBy)
    }

    fun clearFilters() {
        _filterState.value = LoansFilterState()
    }

    private fun applyFilters(loans: List<Loan>, filterState: LoansFilterState): List<Loan> {
        var filtered = loans

        // Apply status filter
        filtered = when (filterState.statusFilter) {
            LoanStatusFilter.ALL -> filtered
            LoanStatusFilter.CURRENT -> filtered.filter { !it.isComplete }
            LoanStatusFilter.CLOSED -> filtered.filter { it.isComplete }
        }

        // Apply sorting
        filtered = when (filterState.sortBy) {
            LoanSortBy.DATE_DESC -> filtered.sortedByDescending { it.startDate }
            LoanSortBy.DATE_ASC -> filtered.sortedBy { it.startDate }
            LoanSortBy.AMOUNT_DESC -> filtered.sortedByDescending { it.totalAmount }
            LoanSortBy.AMOUNT_ASC -> filtered.sortedBy { it.totalAmount }
        }

        return filtered
    }
}
