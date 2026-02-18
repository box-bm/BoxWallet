package com.boxdev.boxwallet.ui.screens.analytics

import android.content.Context
import android.os.Environment
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.domain.model.Transaction
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
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

data class FinancialMetrics(
    val savingsRate: Double,       // (Income - Expense) / Income * 100
    val avgDailySpending: Double,  // Total expense / days in period
    val burnRate: Double,          // Days until balance reaches zero (if current trend continues)
    val incomeGrowth: Double,      // Percentage change in income (recent vs older months)
    val expenseGrowth: Double,     // Percentage change in expenses
    val topSpendingCategory: String,
    val topSpendingAmount: Double
)

data class SpendingInsight(
    val title: String,
    val message: String,
    val type: InsightType
)

enum class InsightType {
    POSITIVE, WARNING, NEUTRAL, ALERT
}

data class AnalyticsUiState(
    val monthlyData: List<MonthlyData> = emptyList(),
    val categoryBreakdown: List<CategoryData> = emptyList(),
    val projectionData: List<ProjectionData> = emptyList(),
    val balanceTrend: List<BalanceTrendData> = emptyList(),
    val financialMetrics: FinancialMetrics? = null,
    val insights: List<SpendingInsight> = emptyList(),
    val selectedPeriod: Int = 6,
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportMessage: String? = null
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    @ApplicationContext private val context: Context
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

                    // Calculate financial metrics
                    val metrics = calculateFinancialMetrics(monthlyData, categoryBreakdown, startDate, endDate)

                    // Generate insights
                    val insights = generateInsights(monthlyData, metrics, categoryBreakdown)

                    _uiState.value = _uiState.value.copy(
                        monthlyData = monthlyData,
                        categoryBreakdown = categoryBreakdown,
                        projectionData = projectionData,
                        balanceTrend = balanceTrend,
                        financialMetrics = metrics,
                        insights = insights,
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
                categoryName = categoriesMap[catId] ?: context.getString(R.string.analytics_uncategorized),
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

    private fun calculateFinancialMetrics(
        monthlyData: List<MonthlyData>,
        categoryBreakdown: List<CategoryData>,
        startDate: LocalDate,
        endDate: LocalDate
    ): FinancialMetrics? {
        if (monthlyData.isEmpty()) return null

        val totalIncome = monthlyData.sumOf { it.income }
        val totalExpense = monthlyData.sumOf { it.expense }

        // Savings rate
        val savingsRate = if (totalIncome > 0) {
            ((totalIncome - totalExpense) / totalIncome) * 100
        } else 0.0

        // Average daily spending
        val daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toDouble()
        val avgDailySpending = if (daysBetween > 0) totalExpense / daysBetween else 0.0

        // Burn rate (days until balance reaches zero if trend continues)
        val netBalance = totalIncome - totalExpense
        val burnRate = if (avgDailySpending > 0 && netBalance < 0) {
            kotlin.math.abs(netBalance / avgDailySpending)
        } else Double.POSITIVE_INFINITY

        // Income and expense growth (compare first half vs second half of period)
        val midPoint = monthlyData.size / 2
        val firstHalfIncome = monthlyData.take(midPoint).sumOf { it.income }
        val secondHalfIncome = monthlyData.drop(midPoint).sumOf { it.income }
        val incomeGrowth = if (firstHalfIncome > 0) {
            ((secondHalfIncome - firstHalfIncome) / firstHalfIncome) * 100
        } else 0.0

        val firstHalfExpense = monthlyData.take(midPoint).sumOf { it.expense }
        val secondHalfExpense = monthlyData.drop(midPoint).sumOf { it.expense }
        val expenseGrowth = if (firstHalfExpense > 0) {
            ((secondHalfExpense - firstHalfExpense) / firstHalfExpense) * 100
        } else 0.0

        // Top spending category
        val topCategory = categoryBreakdown.maxByOrNull { it.amount }

        return FinancialMetrics(
            savingsRate = savingsRate,
            avgDailySpending = avgDailySpending,
            burnRate = burnRate,
            incomeGrowth = incomeGrowth,
            expenseGrowth = expenseGrowth,
            topSpendingCategory = topCategory?.categoryName ?: "N/A",
            topSpendingAmount = topCategory?.amount ?: 0.0
        )
    }

    private fun generateInsights(
        monthlyData: List<MonthlyData>,
        metrics: FinancialMetrics?,
        categoryBreakdown: List<CategoryData>
    ): List<SpendingInsight> {
        if (metrics == null || monthlyData.isEmpty()) return emptyList()

        val totalIncome = monthlyData.sumOf { it.income }
        val totalExpense = monthlyData.sumOf { it.expense }
        val insights = mutableListOf<SpendingInsight>()

        // Savings rate insights — only show when there's actual financial activity
        val rateStr = String.format("%.1f", metrics.savingsRate)
        when {
            metrics.savingsRate >= 20 -> insights.add(
                SpendingInsight(
                    context.getString(R.string.analytics_insight_excellent_savings_title),
                    context.getString(R.string.analytics_insight_excellent_savings_msg, rateStr),
                    InsightType.POSITIVE
                )
            )
            metrics.savingsRate >= 10 -> insights.add(
                SpendingInsight(
                    context.getString(R.string.analytics_insight_good_savings_title),
                    context.getString(R.string.analytics_insight_good_savings_msg, rateStr),
                    InsightType.NEUTRAL
                )
            )
            metrics.savingsRate > 0 -> insights.add(
                SpendingInsight(
                    context.getString(R.string.analytics_insight_low_savings_title),
                    context.getString(R.string.analytics_insight_low_savings_msg, rateStr),
                    InsightType.WARNING
                )
            )
            totalExpense > totalIncome -> insights.add(
                SpendingInsight(
                    context.getString(R.string.analytics_insight_overspending_title),
                    context.getString(R.string.analytics_insight_overspending_msg),
                    InsightType.ALERT
                )
            )
        }

        // Expense growth insight
        if (metrics.expenseGrowth > 15) {
            insights.add(
                SpendingInsight(
                    context.getString(R.string.analytics_insight_rising_expenses_title),
                    context.getString(R.string.analytics_insight_rising_expenses_msg, String.format("%.1f", metrics.expenseGrowth)),
                    InsightType.WARNING
                )
            )
        } else if (metrics.expenseGrowth < -10) {
            insights.add(
                SpendingInsight(
                    context.getString(R.string.analytics_insight_reduced_spending_title),
                    context.getString(R.string.analytics_insight_reduced_spending_msg, String.format("%.1f", kotlin.math.abs(metrics.expenseGrowth))),
                    InsightType.POSITIVE
                )
            )
        }

        // Income growth insight
        if (metrics.incomeGrowth > 10) {
            insights.add(
                SpendingInsight(
                    context.getString(R.string.analytics_insight_income_growth_title),
                    context.getString(R.string.analytics_insight_income_growth_msg, String.format("%.1f", metrics.incomeGrowth)),
                    InsightType.POSITIVE
                )
            )
        } else if (metrics.incomeGrowth < -10) {
            insights.add(
                SpendingInsight(
                    context.getString(R.string.analytics_insight_declining_income_title),
                    context.getString(R.string.analytics_insight_declining_income_msg, String.format("%.1f", kotlin.math.abs(metrics.incomeGrowth))),
                    InsightType.WARNING
                )
            )
        }

        // Top category insight
        if (categoryBreakdown.isNotEmpty()) {
            val topCategory = categoryBreakdown.first()
            if (topCategory.percentage > 40) {
                insights.add(
                    SpendingInsight(
                        context.getString(R.string.analytics_insight_concentrated_title),
                        context.getString(R.string.analytics_insight_concentrated_msg, topCategory.categoryName, String.format("%.0f", topCategory.percentage)),
                        InsightType.NEUTRAL
                    )
                )
            }
        }

        // Burn rate warning
        if (metrics.burnRate < 90 && metrics.burnRate > 0) {
            insights.add(
                SpendingInsight(
                    context.getString(R.string.analytics_insight_burn_rate_title),
                    context.getString(R.string.analytics_insight_burn_rate_msg, metrics.burnRate.toInt()),
                    InsightType.ALERT
                )
            )
        }

        return insights
    }

    fun exportToExcel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, exportMessage = null)

            try {
                val endDate = LocalDate.now()
                val startDate = endDate.minusMonths(_uiState.value.selectedPeriod.toLong())

                val transactions = transactionRepository.getTransactionsBetweenDates(
                    startDate,
                    endDate
                )

                val transactionList = transactions.toList().flatten()

                val filePath = withContext(Dispatchers.IO) {
                    createExcelFile(transactionList, startDate, endDate)
                }

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportMessage = context.getString(R.string.analytics_export_success, filePath)
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportMessage = context.getString(R.string.analytics_export_error, e.message ?: "")
                )
            }
        }
    }

    private fun createExcelFile(transactions: List<Transaction>, startDate: LocalDate, endDate: LocalDate): String {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val boxWalletDir = File(documentsDir, "BoxWallet/Reports")
        if (!boxWalletDir.exists()) {
            boxWalletDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(boxWalletDir, "BoxWallet_Analytics_$timestamp.xlsx")
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Transactions")

            // Header row
            val headerRow = sheet.createRow(0)
            listOf("Date", "Type", "Amount", "Description", "Is Loan", "Installment").forEachIndexed { index, title ->
                headerRow.createCell(index).setCellValue(title)
            }

            // Data rows
            transactions.forEachIndexed { index, transaction ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(transaction.date.format(dateFormatter))
                row.createCell(1).setCellValue(transaction.type.name)
                row.createCell(2).setCellValue(transaction.amount)
                row.createCell(3).setCellValue(transaction.description)
                row.createCell(4).setCellValue(if (transaction.loanId != null) "Yes" else "No")
                row.createCell(5).setCellValue(
                    if (transaction.installmentNumber != null)
                        "${transaction.installmentNumber}"
                    else ""
                )
            }

            // Summary
            val summarySheet = workbook.createSheet("Summary")
            val incomeTotal = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expenseTotal = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

            summarySheet.createRow(0).createCell(0).setCellValue("Period")
            summarySheet.createRow(1).createCell(0).setCellValue(
                "${startDate.format(dateFormatter)} to ${endDate.format(dateFormatter)}"
            )
            summarySheet.createRow(3).createCell(0).setCellValue("Total Income")
            summarySheet.getRow(3).createCell(1).setCellValue(incomeTotal)
            summarySheet.createRow(4).createCell(0).setCellValue("Total Expense")
            summarySheet.getRow(4).createCell(1).setCellValue(expenseTotal)
            summarySheet.createRow(5).createCell(0).setCellValue("Net Balance")
            summarySheet.getRow(5).createCell(1).setCellValue(incomeTotal - expenseTotal)

            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }
        }

        return file.name
    }

    fun clearExportMessage() {
        _uiState.value = _uiState.value.copy(exportMessage = null)
    }
}
