@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.analytics

import com.boxdev.boxwallet.ui.components.LocalDateDialog
import com.boxdev.boxwallet.ui.components.selectableDates
import com.boxdev.boxwallet.ui.components.amountKind
import com.boxdev.boxwallet.ui.components.mainMoney
import com.boxdev.boxwallet.ui.components.Panel
import com.boxdev.boxwallet.ui.components.AppSearchBar
import com.boxdev.boxwallet.ui.components.PaneRow
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.boxdev.boxwallet.ui.components.AmountKind
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.ConnectedChoiceRow
import com.boxdev.boxwallet.ui.components.SectionHeader
import com.boxdev.boxwallet.ui.components.trendIcon
import com.boxdev.boxwallet.ui.components.charts.CategoryDonut
import com.boxdev.boxwallet.ui.components.charts.CashFlowChart
import com.boxdev.boxwallet.ui.components.charts.NetFlowChart
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.boxdev.boxwallet.ui.components.charts.SpendingPatternChart
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.TransactionItem
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import com.boxdev.boxwallet.ui.theme.BoxTheme
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.SegmentedListItem
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.boxdev.boxwallet.data.local.entity.TransactionType
import com.boxdev.boxwallet.ui.components.segmentShapes
import com.boxdev.boxwallet.ui.navigation.NavRoutes
import com.boxdev.boxwallet.ui.navigation.TabReselectEffect
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.util.rememberCurrencyFormatter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateToSearch: () -> Unit = {},
    onEditTransaction: (Long) -> Unit = {},
    onOpenLoan: (Long) -> Unit = {},
    onNavigateToLoans: () -> Unit = {},
    onNavigateToMovements: () -> Unit = {},
    onOpenInstallmentPlan: (Long) -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }
    var showPicker by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Two report columns only where two panes would show (expanded); medium widths keep one column.
    val wide = BoxTheme.layout.twoPane
    val scrollState = rememberScrollState()

    TabReselectEffect(NavRoutes.Analytics.route) {
        scrollState.animateScrollTo(0)
    }

    LaunchedEffect(uiState.exportError) {
        uiState.exportError?.let {
            snackBarHostState.showSnackbar(it)
            viewModel.clearExportState()
        }
    }

    LaunchedEffect(uiState.exportFile) {
        uiState.exportFile?.let { file ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.analytics_export_share)))
            viewModel.clearExportState()
            showExportSheet = false
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            AppSearchBar(
                onClick = onNavigateToSearch,
                trailing = {
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.analytics_export_report))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackBarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }

        val periodEmpty = uiState.transactionCount == 0
        // A single day is too small for shares, rankings or day-over-day swings: it lists its movements instead.
        val daily = uiState.frequency == AnalyticsFrequency.DAILY
        val insightOf = { kind: InsightKind -> uiState.insights.firstOrNull { it.kind == kind } }

        // ---- The selected period ----
        val controls: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ConnectedChoiceRow(
                    options = AnalyticsFrequency.entries,
                    selected = uiState.frequency,
                    onSelect = viewModel::updateFrequency,
                    label = {
                        when (it) {
                            AnalyticsFrequency.DAILY -> stringResource(R.string.analytics_frequency_daily)
                            AnalyticsFrequency.WEEKLY -> stringResource(R.string.analytics_frequency_weekly)
                            AnalyticsFrequency.MONTHLY -> stringResource(R.string.analytics_frequency_monthly)
                            AnalyticsFrequency.YEARLY -> stringResource(R.string.analytics_frequency_yearly)
                        }
                    },
                )
                PeriodNavigator(
                    label = uiState.periodLabel,
                    canGoForward = uiState.periodOffset < 0,
                    showToday = uiState.periodOffset != 0,
                    onBack = { viewModel.navigatePeriod(-1) },
                    onForward = { viewModel.navigatePeriod(1) },
                    onToday = { viewModel.setPeriodOffset(0) },
                    onLabelClick = { showPicker = true },
                )
            }
        }
        val summary: @Composable () -> Unit = { PeriodSummary(uiState) }
        val emptyPeriod: @Composable () -> Unit = {
            if (periodEmpty) {
                Panel {
                    EmptyState(
                        icon = Icons.Outlined.InsertChart,
                        title = stringResource(R.string.analytics_empty_period_title),
                        message = stringResource(R.string.analytics_empty_period_msg),
                    )
                }
            }
        }
        val categories: @Composable () -> Unit = {
            if (!daily && uiState.categoryExpenses.isNotEmpty()) {
                ReportSection(stringResource(R.string.analytics_expenses_by_category)) {
                    CategoryDonut(
                        uiState.categoryExpenses,
                        modifier = Modifier.fillMaxWidth(),
                        onOpenCategory = { category ->
                            viewModel.openCategoryMovements(category.id)
                            onNavigateToMovements()
                        },
                    )
                    insightOf(InsightKind.CONCENTRATION)?.let { SectionNote(it) }
                }
            }
        }
        val pattern: @Composable () -> Unit = {
            when {
                periodEmpty -> Unit
                // Yearly: net per month, so it says something the 12-month cash flow below doesn't.
                uiState.frequency == AnalyticsFrequency.YEARLY -> ReportSection(stringResource(R.string.analytics_net_by_month)) {
                    NetFlowChart(uiState.yearlyMonthlySummary, modifier = Modifier.fillMaxWidth())
                    ChartLegend(
                        stringResource(R.string.analytics_saved),
                        stringResource(R.string.analytics_overspent),
                    )
                }
                uiState.dailySpending.any { it.amount > 0 } -> ReportSection(stringResource(R.string.analytics_spending_pattern)) {
                    SpendingPatternChart(
                        data = uiState.dailySpending,
                        average = uiState.avgDailyExpense,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        val topExpenses: @Composable () -> Unit = {
            val list = if (daily) uiState.dayMovements else uiState.topExpenses
            if (list.isNotEmpty()) {
                ReportSection(
                    stringResource(if (daily) R.string.analytics_day_movements else R.string.analytics_top_expenses)
                ) {
                    Column {
                        list.forEachIndexed { index, transaction ->
                            TransactionItem(
                                transaction = transaction,
                                category = transaction.categoryId?.let { uiState.categories[it] },
                                index = index,
                                count = list.size,
                                showDate = !daily,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                installmentCount = transaction.installmentPlanId?.let(uiState.installmentCounts::get),
                                onClick = {
                                    val planId = transaction.installmentPlanId
                                    if (planId != null) onOpenInstallmentPlan(planId) else onEditTransaction(transaction.id)
                                },
                                modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                            )
                        }
                    }
                }
            }
        }
        val installments: @Composable () -> Unit = {
            uiState.installments?.let { summary ->
                InstallmentsSection(summary, onOpenLoan = onOpenLoan, onSeeLoans = onNavigateToLoans)
            }
        }

        // ---- Always the last 12 months ----
        val yearHeader: @Composable () -> Unit = { TwelveMonthHeader() }
        val cashFlow: @Composable () -> Unit = {
            ReportSection(stringResource(R.string.analytics_cash_flow)) {
                val trimmed = remember(uiState.monthlyTrendData) { trimLeadingEmptyMonths(uiState.monthlyTrendData) }
                if (trimmed.any { it.income > 0 || it.expense > 0 }) {
                    CashFlowChart(data = trimmed, modifier = Modifier.fillMaxWidth())
                    ChartLegend()
                    insightOf(InsightKind.INCOME_TREND)?.let { SectionNote(it) }
                    insightOf(InsightKind.EXPENSE_TREND)?.let { SectionNote(it) }
                } else NoDataPlaceholder()
            }
        }
        val health: @Composable () -> Unit = {
            uiState.financialMetrics?.let { metrics ->
                HealthSection(
                    metrics = metrics,
                    savingsInsight = insightOf(InsightKind.SAVINGS),
                    burnRateInsight = insightOf(InsightKind.BURN_RATE),
                )
            }
        }

        val scroll = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
        val gap = Arrangement.spacedBy(16.dp)
        PaneRow(
            modifier = Modifier.padding(paddingValues),
            first = {
            if (wide) {
                Column(scroll.padding(vertical = 8.dp), verticalArrangement = gap) {
                    // Sections fill whichever column is shorter, so neither ends in a dead block.
                    BalancedColumns(
                        listOf(
                            { controls(); summary(); emptyPeriod() },
                            pattern, categories, installments, topExpenses,
                        )
                    )
                    yearHeader()
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = gap) { cashFlow() }
                        Column(Modifier.weight(1f), verticalArrangement = gap) { health() }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                Column(scroll.padding(vertical = 8.dp), verticalArrangement = gap) {
                    controls(); summary(); emptyPeriod(); categories(); pattern(); topExpenses(); installments()
                    yearHeader(); cashFlow(); health()
                    Spacer(Modifier.height(24.dp))
                }
            }
            },
        )
    }

    if (showPicker) {
        val dismiss = { showPicker = false }
        val confirm: (Int) -> Unit = { offset -> viewModel.setPeriodOffset(offset); showPicker = false }
        when (uiState.frequency) {
            AnalyticsFrequency.DAILY, AnalyticsFrequency.WEEKLY ->
                DailyWeeklyDatePicker(uiState.frequency, uiState.periodOffset, onDismiss = dismiss, onConfirm = confirm)
            AnalyticsFrequency.MONTHLY -> MonthYearPickerDialog(uiState.periodOffset, onDismiss = dismiss, onConfirm = confirm)
            AnalyticsFrequency.YEARLY -> YearPickerDialog(uiState.periodOffset, onDismiss = dismiss, onConfirm = confirm)
        }
    }

    if (showExportSheet) {
        ExportRangeSheet(
            isExporting = uiState.isExporting,
            onDismiss = {
                showExportSheet = false
                viewModel.clearExportState()
            },
            onGenerate = { start, end -> viewModel.exportToExcel(start, end) }
        )
    }
}

/** Drops empty months at the start of a short history, keeping at least six so the axis still reads as a trend. */
private fun trimLeadingEmptyMonths(data: List<MonthlyData>): List<MonthlyData> {
    val first = data.indexOfFirst { it.income > 0 || it.expense > 0 }
    if (first <= 0) return data
    return data.drop(first.coerceAtMost((data.size - 6).coerceAtLeast(0)))
}

/**
 * Net for the period, leading, with a one-line verdict; money in and out beneath it with the
 * change against the previous period.
 */
@Composable
private fun PeriodSummary(state: AnalyticsUiState) {
    val money = BoxTheme.money
    val net = state.periodIncome - state.periodExpense
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
    val formatter = rememberCurrencyFormatter()
    val daily = state.frequency == AnalyticsFrequency.DAILY
    // "vs same point last month" while the period is in progress, so a partial period is never set against a full one.
    val comparisonLabel = when {
        !state.comparisonToDate -> R.string.analytics_vs_previous
        state.frequency == AnalyticsFrequency.WEEKLY -> R.string.analytics_vs_same_point_week
        state.frequency == AnalyticsFrequency.YEARLY -> R.string.analytics_vs_same_point_year
        else -> R.string.analytics_vs_same_point_month
    }
    val verdict = when {
        state.transactionCount == 0 || daily -> null
        state.periodIncome <= 0 -> stringResource(R.string.analytics_verdict_no_income)
        net >= 0 -> stringResource(R.string.analytics_verdict_kept, "%.0f".format(net.toDouble() / state.periodIncome * 100))
        else -> stringResource(R.string.analytics_verdict_over, formatter.formatMain(-net))
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            // The label carries the direction, so a shortfall never reads as "savings".
            Text(
                stringResource(if (net >= 0) R.string.analytics_net_savings else R.string.analytics_overspent),
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
            )
            AmountText(
                amount = mainMoney(kotlin.math.abs(net)),
                kind = amountKind(net >= 0),
                style = BoxTheme.moneyType.hero.copy(fontSize = 44.sp, lineHeight = 48.sp),
                color = onContainer,
                autoSize = true,
            )
            if (verdict != null) {
                Text(verdict, style = MaterialTheme.typography.bodyLarge, color = onContainer)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                FlowAmount(
                    label = stringResource(R.string.analytics_income_label),
                    amount = state.periodIncome, kind = AmountKind.Income,
                    container = money.incomeContainer, content = money.onIncomeContainer,
                    change = percentChange(state.periodIncome, state.previousIncome),
                    showComparison = !daily,
                    comparisonLabel = comparisonLabel,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                FlowAmount(
                    label = stringResource(R.string.analytics_expense_label),
                    amount = state.periodExpense, kind = AmountKind.Expense,
                    container = money.expenseContainer, content = money.onExpenseContainer,
                    change = percentChange(state.periodExpense, state.previousExpense),
                    showComparison = !daily,
                    comparisonLabel = comparisonLabel,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SummaryStat(stringResource(R.string.analytics_movements), state.transactionCount.toString())
                if (state.frequency != AnalyticsFrequency.DAILY) {
                    SummaryStat(stringResource(R.string.analytics_avg_per_day), formatter.formatMain(state.avgDailyExpense))
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
        Text(value, style = BoxTheme.moneyType.small, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun FlowAmount(
    label: String,
    amount: Long,
    kind: AmountKind,
    container: Color,
    content: Color,
    change: Double?,
    showComparison: Boolean,
    @androidx.annotation.StringRes comparisonLabel: Int,
    modifier: Modifier = Modifier,
) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(trendIcon(kind == AmountKind.Income), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            AmountText(mainMoney(amount), kind = kind, style = BoxTheme.moneyType.medium, color = content, autoSize = true)
            if (!showComparison) return@Column
            Spacer(Modifier.height(4.dp))
            if (change == null) {
                Text(
                    stringResource(R.string.analytics_no_previous),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.75f),
                )
            } else {
                // Wraps instead of truncating at large font scales.
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if ("%.0f".format(kotlin.math.abs(change)) != "0") {
                        Icon(
                            if (change > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).padding(top = 1.dp),
                        )
                    }
                    Text(
                        stringResource(comparisonLabel, "%.0f%%".format(kotlin.math.abs(change))),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/** Percent change from [previous] to [current]; null when there is nothing to compare against. */
private fun percentChange(current: Long, previous: Long): Double? =
    if (previous > 0) (current - previous).toDouble() / previous * 100 else null

/**
 * Two equal columns for [sections]. Every way of splitting them between the columns (order kept
 * inside each column, the first section always top-left) is tried, and the split whose columns end
 * closest together wins, so neither column trails off into a dead block. A section that draws
 * nothing takes no space; one that emits several children stacks them.
 */
@Composable
private fun BalancedColumns(sections: List<@Composable () -> Unit>, gap: Dp = 16.dp) {
    Layout(contents = sections) { groups, constraints ->
        val gapPx = gap.roundToPx()
        val colWidth = ((constraints.maxWidth - gapPx) / 2).coerceAtLeast(0)
        val child = Constraints(minWidth = colWidth, maxWidth = colWidth)
        val measured = groups.map { group -> group.map { it.measure(child) }.filter { it.height > 0 } }
            .filter { it.isNotEmpty() }
        val heights = measured.map { ps -> ps.sumOf { it.height } + gapPx * (ps.size - 1) }

        val best = bestColumnSplit(heights, gapPx)

        val y = intArrayOf(0, 0)
        val placements = mutableListOf<Triple<Placeable, Int, Int>>()
        measured.forEachIndexed { i, ps ->
            val col = if (i > 0 && (best shr (i - 1)) and 1 == 1) 1 else 0
            ps.forEach { p ->
                if (y[col] > 0) y[col] += gapPx
                placements += Triple(p, col * (colWidth + gapPx), y[col])
                y[col] += p.height
            }
        }
        layout(constraints.maxWidth, maxOf(y[0], y[1])) {
            placements.forEach { (p, px, py) -> p.place(px, py) }
        }
    }
}

@Composable
private fun ReportSection(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Panel {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)) {
            SectionHeader(title = title, modifier = Modifier.padding(bottom = if (subtitle == null) 8.dp else 0.dp))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 16.dp),
                )
            }
            content()
        }
    }
}

/** Separates the period-driven sections from the ones that always cover the last 12 months. */
@Composable
private fun TwelveMonthHeader() {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            stringResource(R.string.analytics_last_12_months),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .padding(start = 4.dp, top = 24.dp)
                .semantics { heading() },
        )
        Text(
            stringResource(R.string.analytics_twelve_month_caption),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
    }
}

/** Installments that fall due in the period: how many are paid, and the ones still open. */
@Composable
private fun InstallmentsSection(
    summary: InstallmentSummary,
    onOpenLoan: (Long) -> Unit,
    onSeeLoans: () -> Unit,
) {
    val formatter = rememberCurrencyFormatter()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()) }
    val visible = summary.pending.take(3)
    ReportSection(stringResource(R.string.analytics_installments_title)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.analytics_installments_paid, summary.paidCount, summary.dueCount),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (summary.overdueCount > 0) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = CircleShape) {
                        Text(
                            pluralStringResource(R.plurals.analytics_installments_overdue, summary.overdueCount, summary.overdueCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            InstallmentCountBar(summary.states)
            // Money the user owes and money owed to them are never added together.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (summary.toPayDue > 0) {
                    DirectionAmount(
                        label = stringResource(R.string.analytics_installments_to_pay),
                        open = summary.toPayOpen,
                        due = summary.toPayDue,
                        kind = AmountKind.Expense,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (summary.toCollectDue > 0) {
                    DirectionAmount(
                        label = stringResource(R.string.analytics_installments_to_collect),
                        open = summary.toCollectOpen,
                        due = summary.toCollectDue,
                        kind = AmountKind.Income,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (summary.pending.isEmpty()) {
                Text(
                    stringResource(R.string.analytics_installments_all_paid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BoxTheme.money.income,
                )
            } else {
                Column {
                    visible.forEachIndexed { index, due ->
                        InstallmentRow(
                            due = due,
                            dateText = due.firstDue.format(dateFormatter),
                            index = index,
                            count = visible.size,
                            onClick = { onOpenLoan(due.loanId) },
                        )
                    }
                }
                if (summary.pending.size > visible.size) {
                    TextButton(onClick = onSeeLoans, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.analytics_see_loans))
                    }
                }
            }
        }
    }
}

/**
 * One pill per installment due in the period, in due-date order: filled when paid, error when
 * overdue, the track colour while still open. Progress reads as a count, never a mixed amount.
 */
@Composable
private fun InstallmentCountBar(states: List<InstallmentState>) {
    val colors = mapOf(
        InstallmentState.PAID to MaterialTheme.colorScheme.primary,
        InstallmentState.OVERDUE to MaterialTheme.colorScheme.error,
        InstallmentState.OPEN to MaterialTheme.colorScheme.surfaceContainerHighest,
    )
    // Past 24 the pills get too thin; show one bar split into paid, overdue and open runs instead.
    val segments: List<Pair<InstallmentState, Int>> = if (states.size > 24) {
        listOf(InstallmentState.PAID, InstallmentState.OVERDUE, InstallmentState.OPEN)
            .map { st -> st to states.count { it == st } }
            .filter { it.second > 0 }
    } else {
        states.map { it to 1 }
    }
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        segments.forEach { (state, weight) ->
            Box(
                Modifier
                    .weight(weight.toFloat())
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(colors.getValue(state))
            )
        }
    }
}

/** Still-open amount in one direction (to pay or to collect), with the period's total beneath. */
@Composable
private fun DirectionAmount(label: String, open: Long, due: Long, kind: AmountKind, modifier: Modifier = Modifier) {
    val formatter = rememberCurrencyFormatter()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AmountText(mainMoney(open), kind = kind, style = BoxTheme.moneyType.medium, autoSize = true)
        Text(
            stringResource(R.string.analytics_installments_of_due, formatter.formatMain(due)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallmentRow(
    due: LoanInstallmentsDue,
    dateText: String,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val isIncome = due.type == TransactionType.INCOME
    val isOverdue = due.overdueCount > 0
    SegmentedListItem(
        onClick = onClick,
        shapes = segmentShapes(index, count),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
        leadingContent = {
            Icon(
                if (isOverdue) Icons.Outlined.ErrorOutline else Icons.Outlined.Schedule,
                contentDescription = null,
                tint = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            // One row per loan: a count leads when several installments are open.
            val status = if (isOverdue) stringResource(R.string.analytics_installment_overdue, dateText)
            else stringResource(R.string.analytics_installment_due, dateText)
            val counted = when {
                due.count == 1 -> status
                isOverdue -> pluralStringResource(R.plurals.analytics_installments_open, due.count, due.count) + " · " +
                    pluralStringResource(R.plurals.analytics_installments_overdue_since, due.overdueCount, due.overdueCount, dateText)
                else -> pluralStringResource(R.plurals.analytics_installments_open, due.count, due.count) + " · " + status
            }
            Text(
                counted,
                color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            AmountText(
                mainMoney(due.amount),
                kind = amountKind(isIncome),
                style = BoxTheme.moneyType.small,
            )
        },
    ) {
        Text(due.loanName, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Savings rate as a ring with its verdict; the two trend values beneath; burn-rate warning last. */
@Composable
private fun HealthSection(
    metrics: FinancialMetrics,
    savingsInsight: SpendingInsight?,
    burnRateInsight: SpendingInsight?,
) {
    val rate = metrics.savingsRate
    val ringColor = if (rate >= 0) BoxTheme.money.income else BoxTheme.money.expense
    ReportSection(stringResource(R.string.analytics_financial_health)) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
                    val progress = (rate / 100.0).toFloat().coerceIn(0f, 1f)
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(96.dp),
                        color = ringColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Text(
                        "%.0f%%".format(rate),
                        style = BoxTheme.moneyType.medium,
                        color = ringColor,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = BoxTheme.moneyType.medium.fontSize),
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        savingsInsight?.title ?: stringResource(R.string.analytics_savings_rate),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.analytics_savings_rate_caption),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TrendStat(
                    label = stringResource(R.string.analytics_income_trend),
                    growth = metrics.incomeGrowth,
                    increaseIsGood = true,
                    modifier = Modifier.weight(1f),
                )
                TrendStat(
                    label = stringResource(R.string.analytics_expense_trend),
                    growth = metrics.expenseGrowth,
                    increaseIsGood = false,
                    modifier = Modifier.weight(1f),
                )
            }
            // Says what the trend percentages compare, once there is a percentage to explain.
            if (metrics.incomeGrowth != null || metrics.expenseGrowth != null) {
                Text(
                    stringResource(R.string.analytics_trend_caption),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
            burnRateInsight?.let { SectionNote(it, Modifier) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportRangeSheet(
    isExporting: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (LocalDate, LocalDate) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = LocalDate.now()
    val dateFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy") }

    var startDate by remember { mutableStateOf(today.minusMonths(1)) }
    var endDate by remember { mutableStateOf(today) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    if (showStartPicker) {
        LocalDateDialog(
            initial = startDate,
            selectable = selectableDates(max = today),
            onPick = { picked ->
                startDate = picked
                if (endDate.isBefore(picked)) endDate = picked
            },
            onDismiss = { showStartPicker = false },
        )
    }

    if (showEndPicker) {
        LocalDateDialog(
            initial = endDate,
            selectable = selectableDates(max = today, min = startDate),
            onPick = { endDate = it },
            onDismiss = { showEndPicker = false },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.analytics_export_report),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Quick presets
            Text(
                text = stringResource(R.string.analytics_export_quick_range),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val presets = listOf(
                Triple(stringResource(R.string.analytics_export_preset_7d), today.minusDays(6), today),
                Triple(stringResource(R.string.analytics_export_preset_30d), today.minusDays(29), today),
                Triple(stringResource(R.string.analytics_export_preset_3m), today.minusMonths(3).withDayOfMonth(1), today),
                Triple(stringResource(R.string.analytics_export_preset_year), today.withDayOfYear(1), today),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { (label, start, end) ->
                    FilterChip(
                        selected = startDate == start && endDate == end,
                        onClick = { startDate = start; endDate = end },
                        label = { Text(label) }
                    )
                }
            }

            // From / To date fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple(R.string.analytics_export_from, startDate) { showStartPicker = true },
                    Triple(R.string.analytics_export_to, endDate) { showEndPicker = true },
                ).forEach { (label, date, open) ->
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = date.format(dateFormatter),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(label)) },
                            trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(modifier = Modifier.matchParentSize().clickable(onClick = open))
                    }
                }
            }

            Button(
                onClick = { onGenerate(startDate, endDate) },
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExporting) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(stringResource(R.string.analytics_export_generate))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyWeeklyDatePicker(
    frequency: AnalyticsFrequency,
    periodOffset: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val today = LocalDate.now()
    val initialDate = when (frequency) {
        AnalyticsFrequency.DAILY -> today.plusDays(periodOffset.toLong())
        else -> today.with(DayOfWeek.MONDAY).plusWeeks(periodOffset.toLong())
    }
    LocalDateDialog(
        initial = initialDate,
        selectable = selectableDates(max = today),
        onPick = { selectedDate ->
            val offset = when (frequency) {
                AnalyticsFrequency.DAILY -> ChronoUnit.DAYS.between(today, selectedDate).toInt()
                else -> ChronoUnit.WEEKS.between(today.with(DayOfWeek.MONDAY), selectedDate.with(DayOfWeek.MONDAY)).toInt()
            }
            onConfirm(offset)
        },
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthYearPickerDialog(
    periodOffset: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val currentYearMonth = YearMonth.now()
    val initialYearMonth = currentYearMonth.plusMonths(periodOffset.toLong())
    val currentYear = currentYearMonth.year
    val months = java.time.Month.entries

    var selectedMonth by remember { mutableStateOf(initialYearMonth.month) }
    var selectedYear by remember { mutableStateOf(initialYearMonth.year) }
    var monthExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.analytics_select_month)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Month dropdown
                ExposedDropdownMenuBox(
                    expanded = monthExpanded,
                    onExpandedChange = { monthExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedMonth.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.analytics_month)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = monthExpanded,
                        onDismissRequest = { monthExpanded = false }
                    ) {
                        months.forEach { month ->
                            val enabled =
                                !(selectedYear == currentYear && month > currentYearMonth.month)
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        month.getDisplayName(
                                            TextStyle.FULL,
                                            Locale.getDefault()
                                        )
                                    )
                                },
                                onClick = {
                                    selectedMonth = month
                                    monthExpanded = false
                                },
                                enabled = enabled
                            )
                        }
                    }
                }

                YearDropdown(selectedYear) { year ->
                    selectedYear = year
                    // Clamp month if switching to current year
                    if (year == currentYear && selectedMonth > currentYearMonth.month) {
                        selectedMonth = currentYearMonth.month
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(ChronoUnit.MONTHS.between(currentYearMonth, YearMonth.of(selectedYear, selectedMonth)).toInt())
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Year picker covering this year and the 20 before it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearDropdown(selected: Int, onSelect: (Int) -> Unit) {
    val currentYear = LocalDate.now().year
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.analytics_year)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (currentYear downTo currentYear - 20).forEach { year ->
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = {
                        onSelect(year)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearPickerDialog(
    periodOffset: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val currentYear = LocalDate.now().year
    var selectedYear by remember { mutableStateOf(currentYear + periodOffset) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.analytics_select_year)) },
        text = {
            YearDropdown(selectedYear) { selectedYear = it }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(selectedYear - currentYear)
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun PeriodNavigator(
    label: String,
    canGoForward: Boolean,
    showToday: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onToday: () -> Unit,
    onLabelClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Quiet controls: only the selected period above carries a primary fill.
        IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.analytics_previous_period))
        }
        FilledTonalButton(
            onClick = onLabelClick,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showToday) {
            IconButton(onClick = onToday, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Today, contentDescription = stringResource(R.string.analytics_today))
            }
        }
        IconButton(onClick = onForward, enabled = canGoForward, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.analytics_next_period))
        }
    }
}

@Composable
private fun NoDataPlaceholder() {
    Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.analytics_no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChartLegend(
    incomeLabel: String = stringResource(R.string.analytics_income_label),
    expenseLabel: String = stringResource(R.string.analytics_expense_label),
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
        LegendItem(BoxTheme.money.income, incomeLabel)
        LegendItem(BoxTheme.money.expense, expenseLabel)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(width = 16.dp, height = 4.dp).background(color, CircleShape))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * A 12-month trend value. Shows "Not enough history" rather than a made-up 0% when one half of
 * the window is empty; otherwise a signed percent with an arrow, tinted by whether the change is good.
 */
@Composable
private fun TrendStat(
    label: String,
    growth: Double?,
    increaseIsGood: Boolean,
    modifier: Modifier = Modifier,
) {
    val flat = growth == null || signedPercent(growth) == "0.0%"
    val good = if (flat) null else (growth!! > 0) == increaseIsGood
    val valueColor = when (good) {
        true -> BoxTheme.money.income
        false -> BoxTheme.money.expense
        null -> MaterialTheme.colorScheme.onSurface
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (growth == null) {
            Text(
                stringResource(R.string.analytics_not_enough_history),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!flat) {
                    Icon(
                        if (growth > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = valueColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    signedPercent(growth),
                    style = BoxTheme.moneyType.medium,
                    color = valueColor,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = BoxTheme.moneyType.medium.fontSize),
                )
            }
        }
    }
}

/** An insight set inline under the numbers it explains. */
@Composable
private fun SectionNote(insight: SpendingInsight, modifier: Modifier = Modifier.padding(top = 16.dp)) {
    val (icon, tint, container) = when (insight.type) {
        InsightType.POSITIVE -> Triple(Icons.Outlined.CheckCircle, BoxTheme.money.onIncomeContainer, BoxTheme.money.incomeContainer)
        InsightType.WARNING -> Triple(Icons.Outlined.WarningAmber, MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
        InsightType.NEUTRAL -> Triple(Icons.Outlined.Info, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
        InsightType.ALERT -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.onErrorContainer, MaterialTheme.colorScheme.errorContainer)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f)) {
            Text(insight.title, style = MaterialTheme.typography.titleSmall)
            Text(insight.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "+4.2%" / "−1.0%"; an unchanged value is plain "0.0%" so it never reads as a gain. */
private fun signedPercent(v: Double): String = when {
    "%.1f".format(kotlin.math.abs(v)) == "0.0" -> "0.0%"
    v > 0 -> "+%.1f%%".format(v)
    else -> "−%.1f%%".format(-v)
}
