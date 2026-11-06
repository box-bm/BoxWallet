package com.boxdev.boxwallet.ui.screens.analytics

import com.boxdev.boxwallet.analytics.AnalyticsHelper
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.data.repository.CategoryRepository
import com.boxdev.boxwallet.data.repository.LoanRepository
import com.boxdev.boxwallet.data.repository.TransactionRepository
import com.boxdev.boxwallet.util.ExcelReportGenerator
import com.boxdev.boxwallet.domain.model.Category
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.domain.model.monthsBetween
import com.boxdev.boxwallet.domain.model.total
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class AnalyticsFrequency {
    DAILY, WEEKLY, MONTHLY, YEARLY
}

data class MonthlyData(
    val month: YearMonth,
    val income: Double,
    val expense: Double
)

data class CategoryExpenseData(
    val categoryName: String,
    val iconName: String,
    val amount: Double,
    val percentage: Float,
    val color: Color,
    /** The category itself, so the UI can draw its shape token. Null for uncategorized. */
    val category: Category? = null,
)

data class FinancialMetrics(
    val savingsRate: Double,
    val avgDailySpending: Double,
    val burnRate: Double,
    val incomeGrowth: Double,
    val expenseGrowth: Double
)

data class SpendingInsight(
    val title: String,
    val message: String,
    val type: InsightType
)

enum class InsightType {
    POSITIVE, WARNING, NEUTRAL, ALERT
}

private data class FilterState(
    val frequency: AnalyticsFrequency = AnalyticsFrequency.MONTHLY,
    val periodOffset: Int = 0
)

data class AnalyticsUiState(
    val frequency: AnalyticsFrequency = AnalyticsFrequency.MONTHLY,
    val periodOffset: Int = 0,
    val periodLabel: String = "",
    val periodIncome: Double = 0.0,
    val periodExpense: Double = 0.0,
    val categoryExpenses: List<CategoryExpenseData> = emptyList(),
    val monthlyTrendData: List<MonthlyData> = emptyList(),
    val yearlyMonthlySummary: List<MonthlyData> = emptyList(),
    val financialMetrics: FinancialMetrics? = null,
    val insights: List<SpendingInsight> = emptyList(),
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportFile: File? = null,
    val exportError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val loanRepository: LoanRepository,
    @ApplicationContext private val context: Context,
    private val analyticsHelper: AnalyticsHelper
) : ViewModel() {

    private val filterState = MutableStateFlow(FilterState())

    private val _exportState = MutableStateFlow(ExportState())

    private data class ExportState(
        val isExporting: Boolean = false,
        val file: File? = null,
        val error: String? = null
    )

    val uiState: StateFlow<AnalyticsUiState> = filterState
        .flatMapLatest { filter ->
            val (startDate, endDate) = calculateDateRange(filter.frequency, filter.periodOffset)
            val trendStart = LocalDate.now().minusMonths(11).withDayOfMonth(1)
            val trendEnd = LocalDate.now()

            combine(
                transactionRepository.getTransactionsBetweenDates(startDate, endDate),
                transactionRepository.getTransactionsBetweenDates(trendStart, trendEnd),
                categoryRepository.getAllCategories()
            ) { periodTx, trendTx, categories ->
                val categoryExpenses = calculateCategoryExpenses(periodTx, categories.associateBy { it.id })
                val monthlyTrendData = trendTx.monthlyTotals(
                    monthsBetween(YearMonth.from(trendStart), YearMonth.from(trendEnd))
                )
                val yearlyMonthlySummary = if (filter.frequency == AnalyticsFrequency.YEARLY) {
                    val year = startDate.year
                    periodTx.monthlyTotals(monthsBetween(YearMonth.of(year, 1), YearMonth.of(year, 12)))
                } else emptyList()

                val metrics = calculateFinancialMetrics(monthlyTrendData, trendStart, trendEnd)

                AnalyticsUiState(
                    frequency = filter.frequency,
                    periodOffset = filter.periodOffset,
                    periodLabel = formatPeriodLabel(filter.frequency, startDate, endDate),
                    periodIncome = periodTx.total(TransactionType.INCOME),
                    periodExpense = periodTx.total(TransactionType.EXPENSE),
                    categoryExpenses = categoryExpenses,
                    monthlyTrendData = monthlyTrendData,
                    yearlyMonthlySummary = yearlyMonthlySummary,
                    financialMetrics = metrics,
                    insights = generateInsights(monthlyTrendData, metrics, categoryExpenses),
                    isLoading = false
                )
            }
        }
        .flowOn(Dispatchers.Default)
        // Export state is merged last so export progress doesn't recompute the aggregates.
        .combine(_exportState) { state, export ->
            state.copy(isExporting = export.isExporting, exportFile = export.file, exportError = export.error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())

    fun updateFrequency(frequency: AnalyticsFrequency) {
        filterState.value = FilterState(frequency = frequency, periodOffset = 0)
        analyticsHelper.logEvent("analytics_frequency_changed", mapOf("frequency" to frequency.name))
    }

    fun navigatePeriod(delta: Int) {
        val current = filterState.value
        val newOffset = current.periodOffset + delta
        if (newOffset > 0) return
        filterState.value = current.copy(periodOffset = newOffset)
    }

    fun setPeriodOffset(offset: Int) {
        val clamped = offset.coerceAtMost(0)
        filterState.value = filterState.value.copy(periodOffset = clamped)
    }

    private fun calculateDateRange(frequency: AnalyticsFrequency, offset: Int): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (frequency) {
            AnalyticsFrequency.DAILY -> {
                val day = today.plusDays(offset.toLong())
                day to day
            }
            AnalyticsFrequency.WEEKLY -> {
                val thisMonday = today.with(DayOfWeek.MONDAY)
                val startOfWeek = thisMonday.plusWeeks(offset.toLong())
                startOfWeek to startOfWeek.plusDays(6)
            }
            AnalyticsFrequency.MONTHLY -> {
                val month = YearMonth.from(today).plusMonths(offset.toLong())
                month.atDay(1) to month.atEndOfMonth()
            }
            AnalyticsFrequency.YEARLY -> {
                val year = today.year + offset
                LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)
            }
        }
    }

    private fun formatPeriodLabel(frequency: AnalyticsFrequency, start: LocalDate, end: LocalDate): String {
        val locale = Locale.getDefault()
        return when (frequency) {
            AnalyticsFrequency.DAILY -> start.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale))
            AnalyticsFrequency.WEEKLY -> {
                val fmt = DateTimeFormatter.ofPattern("MMM d", locale)
                "${start.format(fmt)} - ${end.format(fmt)}, ${end.year}"
            }
            AnalyticsFrequency.MONTHLY -> start.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
            AnalyticsFrequency.YEARLY -> start.year.toString()
        }
    }

    private fun List<Transaction>.monthlyTotals(months: List<YearMonth>): List<MonthlyData> {
        val byMonth = groupBy { YearMonth.from(it.date) }
        return months.map { month ->
            val monthTx = byMonth[month].orEmpty()
            MonthlyData(month, monthTx.total(TransactionType.INCOME), monthTx.total(TransactionType.EXPENSE))
        }
    }

    private fun calculateCategoryExpenses(
        transactions: List<Transaction>,
        categoriesMap: Map<Long, Category>
    ): List<CategoryExpenseData> {
        val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }
        val totalExpenses = expenseTransactions.sumOf { it.amount }
        if (totalExpenses == 0.0) return emptyList()

        val categoryTotals = expenseTransactions
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amount } }

        val colors = listOf(
            Color(0xFF6200EE), Color(0xFF03DAC5), Color(0xFFFF6F00), Color(0xFFD32F2F),
            Color(0xFF388E3C), Color(0xFF1976D2), Color(0xFFC2185B), Color(0xFF7B1FA2)
        )

        return categoryTotals.entries.mapIndexed { index, (catId, amount) ->
            val category = categoriesMap[catId]
            val name = category?.name ?: context.getString(R.string.analytics_uncategorized)
            val icon = category?.icon ?: "Category"
            CategoryExpenseData(
                categoryName = name,
                iconName = icon,
                amount = amount,
                percentage = ((amount / totalExpenses) * 100).toFloat(),
                color = colors[index % colors.size],
                category = category,
            )
        }.sortedByDescending { it.amount }
    }

    private fun calculateFinancialMetrics(
        monthlyData: List<MonthlyData>,
        startDate: LocalDate,
        endDate: LocalDate
    ): FinancialMetrics? {
        if (monthlyData.isEmpty()) return null

        val totalIncome = monthlyData.sumOf { it.income }
        val totalExpense = monthlyData.sumOf { it.expense }

        val savingsRate = if (totalIncome > 0) {
            ((totalIncome - totalExpense) / totalIncome) * 100
        } else 0.0

        val daysBetween = ChronoUnit.DAYS.between(startDate, endDate).toDouble()
        val avgDailySpending = if (daysBetween > 0) totalExpense / daysBetween else 0.0

        val netBalance = totalIncome - totalExpense
        val burnRate = if (avgDailySpending > 0 && netBalance < 0) {
            kotlin.math.abs(netBalance / avgDailySpending)
        } else Double.POSITIVE_INFINITY

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

        return FinancialMetrics(
            savingsRate = savingsRate,
            avgDailySpending = avgDailySpending,
            burnRate = burnRate,
            incomeGrowth = incomeGrowth,
            expenseGrowth = expenseGrowth
        )
    }

    private fun generateInsights(
        monthlyData: List<MonthlyData>,
        metrics: FinancialMetrics?,
        categoryBreakdown: List<CategoryExpenseData>
    ): List<SpendingInsight> {
        if (metrics == null || monthlyData.isEmpty()) return emptyList()

        val totalIncome = monthlyData.sumOf { it.income }
        val totalExpense = monthlyData.sumOf { it.expense }
        val insights = mutableListOf<SpendingInsight>()

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

    fun exportToExcel(startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            _exportState.value = ExportState(isExporting = true)
            try {
                val transactionList = transactionRepository.getTransactionsBetweenDates(startDate, endDate).first()
                val categories = categoryRepository.getAllCategories().first().associate { it.id to it.name }
                val loans = loanRepository.getAllLoans().first().associateBy { it.id }

                val file = withContext(Dispatchers.IO) {
                    val reportsDir = File(context.cacheDir, "reports").also { if (!it.exists()) it.mkdirs() }
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val f = File(reportsDir, "BoxWallet_Report_$timestamp.xlsx")
                    ExcelReportGenerator.create(f, transactionList, categories, loans, startDate, endDate)
                }

                _exportState.value = ExportState(file = file)
                analyticsHelper.logEvent("analytics_exported")
            } catch (e: Exception) {
                _exportState.value = ExportState(error = context.getString(R.string.analytics_export_error, e.message ?: ""))
            }
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState()
    }
}
