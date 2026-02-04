package com.boxdev.boxwallet.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.FinancialSummary
import com.boxdev.boxwallet.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val financialSummary: FinancialSummary = FinancialSummary(),
    val latestExpenses: List<Transaction> = emptyList(),
    val latestIncomes: List<Transaction> = emptyList(),
    val isLoading: Boolean = true,
    val selectedTransactions: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _selectedTransactions = MutableStateFlow<Set<Long>>(emptySet())
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<HomeUiState> = combine(
        transactionRepository.getFinancialSummary(),
        transactionRepository.getLatestExpenses(7),
        transactionRepository.getLatestIncomes(5),
        _selectedTransactions,
        _isLoading
    ) { summary, expenses, incomes, selected, loading ->
        HomeUiState(
            financialSummary = summary,
            latestExpenses = expenses,
            latestIncomes = incomes,
            isLoading = false,
            selectedTransactions = selected,
            isSelectionMode = selected.isNotEmpty()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun toggleTransactionSelection(transactionId: Long) {
        _selectedTransactions.value = if (_selectedTransactions.value.contains(transactionId)) {
            _selectedTransactions.value - transactionId
        } else {
            _selectedTransactions.value + transactionId
        }
    }

    fun clearSelection() {
        _selectedTransactions.value = emptySet()
    }

    fun deleteSelectedTransactions() {
        viewModelScope.launch {
            transactionRepository.deleteTransactions(_selectedTransactions.value.toList())
            _selectedTransactions.value = emptySet()
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.deleteTransaction(id)
        }
    }
}
