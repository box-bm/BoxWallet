package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanStatus
import com.boxdev.boxwallet.domain.model.LoanTotals
import com.boxdev.boxwallet.domain.model.LoanType
import com.boxdev.boxwallet.domain.model.outstandingTotals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

enum class LoanSortBy { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }

data class LoansFilterState(
    /** Null shows every status. */
    val statusFilter: LoanStatus? = null,
    /** Null shows both directions. */
    val loanTypeFilter: LoanType? = null,
    val categoryId: Long? = null,
    val sortBy: LoanSortBy = LoanSortBy.DATE_DESC
)

data class LoansUiState(
    val loans: List<Loan> = emptyList(),
    val filteredLoans: List<Loan> = emptyList(),
    val totals: LoanTotals = LoanTotals(0, 0),
    val categories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val filterState: LoansFilterState = LoansFilterState()
)

@HiltViewModel
class LoansViewModel @Inject constructor(
    loanRepository: LoanRepository,
    categoryRepository: CategoryRepository
) : ViewModel() {

    private val _filterState = MutableStateFlow(LoansFilterState())

    val uiState: StateFlow<LoansUiState> = combine(
        loanRepository.getAllLoans(),
        categoryRepository.getAllCategories(),
        _filterState
    ) { loans, categories, filterState ->
        LoansUiState(
            loans = loans,
            filteredLoans = applyFilters(loans, filterState),
            totals = loans.outstandingTotals(),
            categories = categories,
            isLoading = false,
            filterState = filterState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoansUiState()
    )

    fun updateStatusFilter(status: LoanStatus?) {
        _filterState.update { it.copy(statusFilter = status) }
    }

    fun updateLoanTypeFilter(type: LoanType?) {
        _filterState.update { it.copy(loanTypeFilter = type) }
    }

    fun updateCategoryFilter(categoryId: Long?) {
        _filterState.update { it.copy(categoryId = categoryId) }
    }

    fun updateSortBy(sortBy: LoanSortBy) {
        _filterState.update { it.copy(sortBy = sortBy) }
    }

    fun clearFilters() {
        _filterState.value = LoansFilterState()
    }

    private fun applyFilters(loans: List<Loan>, filter: LoansFilterState): List<Loan> {
        val today = LocalDate.now()
        val filtered = loans.filter { loan ->
            (filter.loanTypeFilter == null || loan.loanType == filter.loanTypeFilter) &&
                (filter.statusFilter == null || loan.status(today) == filter.statusFilter) &&
                (filter.categoryId == null || loan.categoryId == filter.categoryId)
        }
        return when (filter.sortBy) {
            LoanSortBy.DATE_DESC -> filtered.sortedByDescending { it.startDate }
            LoanSortBy.DATE_ASC -> filtered.sortedBy { it.startDate }
            LoanSortBy.AMOUNT_DESC -> filtered.sortedByDescending { it.totalAmount.toMajor() }
            LoanSortBy.AMOUNT_ASC -> filtered.sortedBy { it.totalAmount.toMajor() }
        }
    }
}
