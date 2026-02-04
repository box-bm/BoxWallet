package com.boxdev.boxwallet.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class MonthlyData(
    val month: YearMonth,
    val income: Double,
    val expense: Double
)

data class AnalyticsUiState(
    val monthlyData: List<MonthlyData> = emptyList(),
    val selectedPeriod: Int = 6, // months
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun updatePeriod(months: Int) {
        _uiState.value = _uiState.value.copy(selectedPeriod = months)
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val endDate = LocalDate.now()
            val startDate = endDate.minusMonths(_uiState.value.selectedPeriod.toLong())

            val transactions = transactionRepository.getTransactionsBetweenDatesSync(startDate, endDate)

            val monthlyData = calculateMonthlyData(transactions, startDate, endDate)

            _uiState.value = _uiState.value.copy(
                monthlyData = monthlyData,
                isLoading = false
            )
        }
    }

    private fun calculateMonthlyData(
        transactions: List<Transaction>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<MonthlyData> {
        val months = mutableListOf<YearMonth>()
        var current = YearMonth.from(startDate)
        val end = YearMonth.from(endDate)

        while (!current.isAfter(end)) {
            months.add(current)
            current = current.plusMonths(1)
        }

        return months.map { month ->
            val monthTransactions = transactions.filter {
                YearMonth.from(it.date) == month
            }

            val income = monthTransactions
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amount }

            val expense = monthTransactions
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }

            MonthlyData(month, income, expense)
        }
    }
}
