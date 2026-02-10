package com.boxdev.boxwallet.ui.screens.analytics

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

data class MonthlyData(
    val month: YearMonth,
    val income: Double,
    val expense: Double
)

data class CategoryData(
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val color: Color
)

data class ProjectionData(
    val month: YearMonth,
    val projected: Double,
    val actual: Double?
)

data class BalanceTrendData(
    val month: YearMonth,
    val balance: Double,
    val isIncreasing: Boolean
)

data class AnalyticsUiState(
    val monthlyData: List<MonthlyData> = emptyList(),
    val categoryBreakdown: List<CategoryData> = emptyList(),
    val projectionData: List<ProjectionData> = emptyList(),
    val balanceTrend: List<BalanceTrendData> = emptyList(),
    val selectedPeriod: Int = 6,
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
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

            transactionRepository.getTransactionsBetweenDates(startDate, endDate)
                .collect { transactions ->
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
                            income = monthTx.filter { it.type == TransactionType.INCOME }
                                .sumOf { it.amount },
                            expense = monthTx.filter { it.type == TransactionType.EXPENSE }
                                .sumOf { it.amount }
                        )
                    }

                    // Calculate category breakdown
                    val categoryBreakdown = calculateCategoryBreakdown(transactions)

                    // Calculate projections
                    val projectionData = calculateProjections(monthlyData)

                    // Calculate balance trend
                    val balanceTrend = calculateBalanceTrend(monthlyData)

                    _uiState.value = _uiState.value.copy(
                        monthlyData = monthlyData,
                        categoryBreakdown = categoryBreakdown,
                        projectionData = projectionData,
                        balanceTrend = balanceTrend,
                        isLoading = false
                    )
                }
        }
    }

    private suspend fun calculateCategoryBreakdown(transactions: List<Transaction>): List<CategoryData> {
        val categoriesMap = categoryRepository.getAllCategories()
            .first()
            .associate { it.id to it.name }


        val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }
        val totalExpenses = expenseTransactions.sumOf { it.amount }

        if (totalExpenses == 0.0) return emptyList()

        val categoryTotals = expenseTransactions
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }

        val colors = listOf(
            Color(0xFF6200EE),
            Color(0xFF03DAC5),
            Color(0xFFFF6F00),
            Color(0xFFD32F2F),
            Color(0xFF388E3C),
            Color(0xFF1976D2),
            Color(0xFFC2185B),
            Color(0xFF7B1FA2)
        )

        return categoryTotals.entries.mapIndexed { index, (catId, amount) ->
            CategoryData(
                categoryName = categoriesMap[catId] ?: "Uncategorized",
                amount = amount,
                percentage = ((amount / totalExpenses) * 100).toFloat(),
                color = colors[index % colors.size]
            )
        }.sortedByDescending { it.amount }
    }

    private fun calculateProjections(monthlyData: List<MonthlyData>): List<ProjectionData> {
        if (monthlyData.isEmpty()) return emptyList()

        // Calculate average expense from historical data
        val avgExpense = monthlyData.map { it.expense }.average()

        // Project 3 months into the future
        val lastMonth = monthlyData.last().month
        return (1..3).map { i ->
            ProjectionData(
                month = lastMonth.plusMonths(i.toLong()),
                projected = avgExpense,
                actual = null
            )
        }
    }

    private fun calculateBalanceTrend(monthlyData: List<MonthlyData>): List<BalanceTrendData> {
        if (monthlyData.isEmpty()) return emptyList()

        var cumulativeBalance = 0.0
        return monthlyData.map { data ->
            val netChange = data.income - data.expense
            cumulativeBalance += netChange
            BalanceTrendData(
                month = data.month,
                balance = cumulativeBalance,
                isIncreasing = netChange >= 0
            )
        }
    }
}
