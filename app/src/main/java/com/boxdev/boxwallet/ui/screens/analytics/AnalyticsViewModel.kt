package com.boxdev.boxwallet.ui.screens.analytics

import com.boxdev.boxwallet.analytics.AnalyticsHelper
import android.content.Context
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
import com.boxdev.boxwallet.ui.navigation.MovementsFilterRequest

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
    /** The category itself, so the UI can draw its shape token. Null for uncategorized or "Other". */
    val category: Category? = null,
    /** True for the bucket that folds the long tail of small categories together. */
    val isOther: Boolean = false,
)

/** Spending on one day of the period; [isFuture] days have not happened yet. */
data class DailySpend(
    val date: LocalDate,
    val amount: Double,
    val isFuture: Boolean,
)

data class FinancialMetrics(
    val savingsRate: Double,
    val avgDailySpending: Double,
    val burnRate: Double,
    /** Change between the two halves of the 12-month window; null when either half has no data. */
    val incomeGrowth: Double?,
    val expenseGrowth: Double?,
)

/** Which part of the report an insight belongs to, so it can sit beside the numbers it explains. */
enum class InsightKind { SAVINGS, INCOME_TREND, EXPENSE_TREND, CONCENTRATION, BURN_RATE }

data class SpendingInsight(
    val title: String,
    val message: String,
    val type: InsightType,
    val kind: InsightKind,
)

/** One loan installment due in the period, with the loan it belongs to. */
data class InstallmentDue(
    val installment: Transaction,
    val loanId: Long,
    val loanName: String,
    val isOverdue: Boolean,
)

/** Loan installments that fall due inside the selected period. */
data class InstallmentSummary(
    val dueCount: Int,
    val paidCount: Int,
    val overdueCount: Int,
    /** Installments the user pays (expense loans): total due and what is still open. */
    val toPayDue: Double,
    val toPayOpen: Double,
    /** Installments owed to the user (income loans). Never added to [toPayDue]. */
    val toCollectDue: Double,
    val toCollectOpen: Double,
    /** Paid flags in due-date order, one per installment, for the count bar. */
    val paidFlags: List<Boolean>,
    /** Unpaid installments, oldest first: overdue ones lead. */
    val pending: List<InstallmentDue>,
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
    /** Totals for the period just before this one, for the "vs previous" deltas. */
    val previousIncome: Double = 0.0,
    val previousExpense: Double = 0.0,
    val transactionCount: Int = 0,
    /** Expense per day across the period (weekly and monthly views only). */
    val dailySpending: List<DailySpend> = emptyList(),
    /** Expense divided by the days of the period that have already happened. */
    val avgDailyExpense: Double = 0.0,
    val topExpenses: List<Transaction> = emptyList(),
    /** Every movement of the day, newest first (Daily view only). */
    val dayMovements: List<Transaction> = emptyList(),
    val categories: Map<Long, Category> = emptyMap(),
    /** Null when no installment falls due in the period. */
    val installments: InstallmentSummary? = null,
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

private const val MAX_CATEGORY_SLICES = 5

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val loanRepository: LoanRepository,
    @ApplicationContext private val context: Context,
    private val analyticsHelper: AnalyticsHelper,
    private val movementsFilter: MovementsFilterRequest,
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
            val (prevStart, prevEnd) = calculateDateRange(filter.frequency, filter.periodOffset - 1)
            val trendStart = LocalDate.now().minusMonths(11).withDayOfMonth(1)
            val trendEnd = LocalDate.now()

            val installmentsFlow = combine(
                transactionRepository.getInstallmentsBetweenDates(startDate, endDate),
                loanRepository.getAllLoans(),
            ) { installments, loans -> summarizeInstallments(installments, loans.associate { it.id to it.name }) }

            combine(
                transactionRepository.getTransactionsBetweenDates(startDate, endDate),
                transactionRepository.getTransactionsBetweenDates(prevStart, prevEnd),
                transactionRepository.getTransactionsBetweenDates(trendStart, trendEnd),
                categoryRepository.getAllCategories(),
                installmentsFlow,
            ) { periodTx, previousTx, trendTx, categories, installments ->
                val categoryMap = categories.associateBy { it.id }
                val categoryExpenses = calculateCategoryExpenses(periodTx, categoryMap)
                val today = LocalDate.now()
                val expenses = periodTx.filter { it.type == TransactionType.EXPENSE }
                val dailySpending = if (filter.frequency == AnalyticsFrequency.WEEKLY || filter.frequency == AnalyticsFrequency.MONTHLY) {
                    val byDay = expenses.groupBy { it.date.toLocalDate() }
                    generateSequence(startDate) { it.plusDays(1) }
                        .takeWhile { !it.isAfter(endDate) }
                        .map { day -> DailySpend(day, byDay[day].orEmpty().sumOf { it.amount }, day.isAfter(today)) }
                        .toList()
                } else emptyList()
                val elapsedDays = (ChronoUnit.DAYS.between(startDate, minOf(endDate, today)) + 1).coerceAtLeast(1)
                val periodExpense = periodTx.total(TransactionType.EXPENSE)
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
                    periodExpense = periodExpense,
                    previousIncome = previousTx.total(TransactionType.INCOME),
                    previousExpense = previousTx.total(TransactionType.EXPENSE),
                    transactionCount = periodTx.size,
                    dailySpending = dailySpending,
                    avgDailyExpense = if (startDate.isAfter(today)) 0.0 else periodExpense / elapsedDays,
                    topExpenses = expenses.sortedByDescending { it.amount }.take(5),
                    dayMovements = if (filter.frequency == AnalyticsFrequency.DAILY) periodTx.sortedByDescending { it.date } else emptyList(),
                    categories = categoryMap,
                    installments = installments,
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

    /** Asks the Movements tab to open filtered to [categoryId]; the screen then switches tabs. */
    fun openCategoryMovements(categoryId: Long) {
        movementsFilter.request(categoryId)
    }

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

        val ranked = categoryTotals.entries.map { (catId, amount) ->
            val category = categoriesMap[catId]
            CategoryExpenseData(
                categoryName = category?.name ?: context.getString(R.string.analytics_uncategorized),
                iconName = category?.icon ?: "Category",
                amount = amount,
                percentage = ((amount / totalExpenses) * 100).toFloat(),
                category = category,
            )
        }.sortedByDescending { it.amount }

        // Keep the chart readable: the top five, then everything else folded into "Other".
        if (ranked.size <= MAX_CATEGORY_SLICES + 1) return ranked
        val tail = ranked.drop(MAX_CATEGORY_SLICES)
        val otherAmount = tail.sumOf { it.amount }
        return ranked.take(MAX_CATEGORY_SLICES) + CategoryExpenseData(
            categoryName = context.getString(R.string.analytics_other),
            iconName = "MoreHoriz",
            amount = otherAmount,
            percentage = ((otherAmount / totalExpenses) * 100).toFloat(),
            isOther = true,
        )
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
        val incomeGrowth = if (firstHalfIncome > 0 && secondHalfIncome > 0) {
            ((secondHalfIncome - firstHalfIncome) / firstHalfIncome) * 100
        } else null

        val firstHalfExpense = monthlyData.take(midPoint).sumOf { it.expense }
        val secondHalfExpense = monthlyData.drop(midPoint).sumOf { it.expense }
        val expenseGrowth = if (firstHalfExpense > 0 && secondHalfExpense > 0) {
            ((secondHalfExpense - firstHalfExpense) / firstHalfExpense) * 100
        } else null

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
        fun add(title: Int, message: String, type: InsightType, kind: InsightKind) =
            insights.add(SpendingInsight(context.getString(title), message, type, kind))
        fun pct(v: Double, decimals: Int = 1) = String.format("%.${decimals}f", kotlin.math.abs(v))

        val rateStr = pct(metrics.savingsRate)
        when {
            metrics.savingsRate >= 20 -> add(R.string.analytics_insight_excellent_savings_title,
                context.getString(R.string.analytics_insight_excellent_savings_msg, rateStr), InsightType.POSITIVE, InsightKind.SAVINGS)
            metrics.savingsRate >= 10 -> add(R.string.analytics_insight_good_savings_title,
                context.getString(R.string.analytics_insight_good_savings_msg, rateStr), InsightType.NEUTRAL, InsightKind.SAVINGS)
            metrics.savingsRate > 0 -> add(R.string.analytics_insight_low_savings_title,
                context.getString(R.string.analytics_insight_low_savings_msg, rateStr), InsightType.WARNING, InsightKind.SAVINGS)
            totalExpense > totalIncome -> add(R.string.analytics_insight_overspending_title,
                context.getString(R.string.analytics_insight_overspending_msg), InsightType.ALERT, InsightKind.SAVINGS)
        }

        metrics.expenseGrowth?.let { g ->
            if (g > 15) add(R.string.analytics_insight_rising_expenses_title,
                context.getString(R.string.analytics_insight_rising_expenses_msg, pct(g)), InsightType.WARNING, InsightKind.EXPENSE_TREND)
            else if (g < -10) add(R.string.analytics_insight_reduced_spending_title,
                context.getString(R.string.analytics_insight_reduced_spending_msg, pct(g)), InsightType.POSITIVE, InsightKind.EXPENSE_TREND)
        }

        metrics.incomeGrowth?.let { g ->
            if (g > 10) add(R.string.analytics_insight_income_growth_title,
                context.getString(R.string.analytics_insight_income_growth_msg, pct(g)), InsightType.POSITIVE, InsightKind.INCOME_TREND)
            else if (g < -10) add(R.string.analytics_insight_declining_income_title,
                context.getString(R.string.analytics_insight_declining_income_msg, pct(g)), InsightType.WARNING, InsightKind.INCOME_TREND)
        }

        categoryBreakdown.firstOrNull { !it.isOther }?.let { top ->
            if (top.percentage > 40) add(R.string.analytics_insight_concentrated_title,
                context.getString(R.string.analytics_insight_concentrated_msg, top.categoryName, pct(top.percentage.toDouble(), 0)),
                InsightType.NEUTRAL, InsightKind.CONCENTRATION)
        }

        if (metrics.burnRate < 90 && metrics.burnRate > 0) {
            add(R.string.analytics_insight_burn_rate_title,
                context.getString(R.string.analytics_insight_burn_rate_msg, metrics.burnRate.toInt()), InsightType.ALERT, InsightKind.BURN_RATE)
        }

        return insights
    }

    private fun summarizeInstallments(installments: List<Transaction>, loanNames: Map<Long, String>): InstallmentSummary? {
        if (installments.isEmpty()) return null
        val today = LocalDate.now()
        val paid = installments.filter { it.isCleared }
        val pending = installments.filter { !it.isCleared }.map { tx ->
            InstallmentDue(
                installment = tx,
                loanId = tx.loanId ?: 0L,
                loanName = tx.loanId?.let { loanNames[it] } ?: tx.description,
                isOverdue = tx.date.toLocalDate().isBefore(today),
            )
        }
        return InstallmentSummary(
            dueCount = installments.size,
            paidCount = paid.size,
            overdueCount = pending.count { it.isOverdue },
            toPayDue = installments.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
            toPayOpen = installments.filter { it.type == TransactionType.EXPENSE && !it.isCleared }.sumOf { it.amount },
            toCollectDue = installments.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            toCollectOpen = installments.filter { it.type == TransactionType.INCOME && !it.isCleared }.sumOf { it.amount },
            paidFlags = installments.map { it.isCleared },
            pending = pending,
        )
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
