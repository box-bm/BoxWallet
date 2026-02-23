package com.boxdev.boxwallet.ui.screens.loans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Loan
import com.boxdev.boxwallet.domain.model.LoanType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

enum class LoanStatusFilter { ALL, CURRENT, OVERDUE, PAID }
enum class LoanTypeFilter { ALL, INCOME, EXPENSE }
enum class LoanSortBy { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }

data class LoansFilterState(
    val searchQuery: String = "",
    val statusFilter: LoanStatusFilter = LoanStatusFilter.ALL,
    val loanTypeFilter: LoanTypeFilter = LoanTypeFilter.ALL,
    val categoryId: Long? = null,
    val sortBy: LoanSortBy = LoanSortBy.DATE_DESC
)

data class LoansUiState(
    val loans: List<Loan> = emptyList(),
    val filteredLoans: List<Loan> = emptyList(),
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
        val filteredLoans = applyFilters(loans, categories, filterState)
        LoansUiState(
            loans = loans,
            filteredLoans = filteredLoans,
            categories = categories,
            isLoading = false,
            filterState = filterState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LoansUiState()
    )

    fun updateSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun updateStatusFilter(status: LoanStatusFilter) {
        _filterState.value = _filterState.value.copy(statusFilter = status)
    }

    fun updateLoanTypeFilter(type: LoanTypeFilter) {
        _filterState.value = _filterState.value.copy(loanTypeFilter = type)
    }

    fun updateCategoryFilter(categoryId: Long?) {
        _filterState.value = _filterState.value.copy(categoryId = categoryId)
    }

    fun updateSortBy(sortBy: LoanSortBy) {
        _filterState.value = _filterState.value.copy(sortBy = sortBy)
    }

    fun clearFilters() {
        _filterState.value = LoansFilterState()
    }

    private fun applyFilters(
        loans: List<Loan>,
        categories: List<Category>,
        filterState: LoansFilterState
    ): List<Loan> {
        var filtered = loans
        val today = LocalDate.now()

        if (filterState.searchQuery.isNotBlank()) {
            val query = filterState.searchQuery.trim()
            val categoryNameMap = categories.associate { it.id to it.name }
            filtered = filtered.filter { loan ->
                loan.name.contains(query, ignoreCase = true) ||
                loan.description.contains(query, ignoreCase = true) ||
                (loan.categoryId != null &&
                    categoryNameMap[loan.categoryId]?.contains(query, ignoreCase = true) == true)
            }
        }

        filtered = when (filterState.loanTypeFilter) {
            LoanTypeFilter.ALL -> filtered
            LoanTypeFilter.INCOME -> filtered.filter { it.loanType == LoanType.INCOME }
            LoanTypeFilter.EXPENSE -> filtered.filter { it.loanType == LoanType.EXPENSE }
        }

        filtered = when (filterState.statusFilter) {
            LoanStatusFilter.ALL -> filtered
            LoanStatusFilter.CURRENT -> filtered.filter {
                !it.isComplete && it.nextDueDate?.isBefore(today) != true
            }
            LoanStatusFilter.OVERDUE -> filtered.filter {
                !it.isComplete && it.nextDueDate?.isBefore(today) == true
            }
            LoanStatusFilter.PAID -> filtered.filter { it.isComplete }
        }

        filterState.categoryId?.let { cid ->
            filtered = filtered.filter { it.categoryId == cid }
        }

        filtered = when (filterState.sortBy) {
            LoanSortBy.DATE_DESC -> filtered.sortedByDescending { it.startDate }
            LoanSortBy.DATE_ASC -> filtered.sortedBy { it.startDate }
            LoanSortBy.AMOUNT_DESC -> filtered.sortedByDescending { it.totalAmount }
            LoanSortBy.AMOUNT_ASC -> filtered.sortedBy { it.totalAmount }
        }

        return filtered
    }
}
