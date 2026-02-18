package com.boxdev.boxwallet.ui.screens.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class TransactionTypeFilter { ALL, INCOME, EXPENSE }
enum class SortBy { DATE_ASC, DATE_DESC, AMOUNT_ASC, AMOUNT_DESC }

data class TransactionsFilterState(
    val searchQuery: String = "",
    val transactionType: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val categoryId: Long? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val sortBy: SortBy = SortBy.DATE_DESC,
    val showOnlyRecurrent: Boolean = false
)

data class TransactionsListUiState(
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    val availableCategories: List<Category> = emptyList(),
    val isLoading: Boolean = true,
    val filterState: TransactionsFilterState = TransactionsFilterState()
)

@HiltViewModel
class TransactionsListViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsListUiState())
    val uiState: StateFlow<TransactionsListUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect {
                _uiState.value = _uiState.value.copy(availableCategories = it)
            }
        }
        viewModelScope.launch {
            repository.getAllTransactions().collect { transactions ->
                _uiState.value = _uiState.value.copy(transactions = transactions, isLoading = false)
                applyFilters()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(filterState = _uiState.value.filterState.copy(searchQuery = query))
        applyFilters()
    }

    fun updateTypeFilter(type: TransactionTypeFilter) {
        _uiState.value = _uiState.value.copy(filterState = _uiState.value.filterState.copy(transactionType = type))
        applyFilters()
    }

    fun updateCategoryFilter(categoryId: Long?) {
        _uiState.value = _uiState.value.copy(filterState = _uiState.value.filterState.copy(categoryId = categoryId))
        applyFilters()
    }

    fun updateDateRange(startDate: LocalDate?, endDate: LocalDate?) {
        _uiState.value = _uiState.value.copy(filterState = _uiState.value.filterState.copy(startDate = startDate, endDate = endDate))
        applyFilters()
    }

    fun updateSortBy(sortBy: SortBy) {
        _uiState.value = _uiState.value.copy(filterState = _uiState.value.filterState.copy(sortBy = sortBy))
        applyFilters()
    }

    fun updateRecurrentFilter(showOnlyRecurrent: Boolean) {
        _uiState.value = _uiState.value.copy(filterState = _uiState.value.filterState.copy(showOnlyRecurrent = showOnlyRecurrent))
        applyFilters()
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(filterState = TransactionsFilterState())
        applyFilters()
    }

    private fun applyFilters() {
        val s = _uiState.value
        var filtered = s.transactions

        if (s.filterState.searchQuery.isNotBlank()) {
            filtered = filtered.filter {
                it.description.contains(s.filterState.searchQuery, ignoreCase = true)
            }
        }

        filtered = when (s.filterState.transactionType) {
            TransactionTypeFilter.INCOME -> filtered.filter { it.type == TransactionType.INCOME }
            TransactionTypeFilter.EXPENSE -> filtered.filter { it.type == TransactionType.EXPENSE }
            TransactionTypeFilter.ALL -> filtered
        }

        s.filterState.categoryId?.let { cid ->
            filtered = filtered.filter { it.categoryId == cid }
        }

        if (s.filterState.startDate != null || s.filterState.endDate != null) {
            filtered = filtered.filter {
                val txDate = it.date.toLocalDate()
                (s.filterState.startDate?.let { start -> !txDate.isBefore(start) } ?: true) &&
                (s.filterState.endDate?.let { end -> !txDate.isAfter(end) } ?: true)
            }
        }

        if (s.filterState.showOnlyRecurrent) {
            filtered = filtered.filter { it.isRecurrent }
        }

        filtered = when (s.filterState.sortBy) {
            SortBy.DATE_ASC -> filtered.sortedBy { it.date }
            SortBy.DATE_DESC -> filtered.sortedByDescending { it.date }
            SortBy.AMOUNT_ASC -> filtered.sortedBy { it.amount }
            SortBy.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
        }

        _uiState.value = _uiState.value.copy(filteredTransactions = filtered)
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repository.deleteTransaction(id) }
    }
}
