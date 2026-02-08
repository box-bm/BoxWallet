package com.boxdev.boxwallet.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.TransactionRepository
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
    val selectedPeriod: Int = 6,
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

            transactionRepository.getTransactionsBetweenDates(startDate, endDate).collect { transactions ->
                val months = mutableListOf<YearMonth>()
                var current = YearMonth.from(startDate)
                val end = YearMonth.from(endDate)
                while (!current.isAfter(end)) {
                    months.add(current)
                    current = current.plusMonths(1)
                }

                val monthlyData = months.map { month ->
                    val monthTx = transactions.filter { YearMonth.from(it.date) == month }
                    MonthlyData(
                        month = month,
                        income = monthTx.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                        expense = monthTx.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    )
                }
                _uiState.value = _uiState.value.copy(monthlyData = monthlyData, isLoading = false)
            }
        }
    }
}
