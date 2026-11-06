@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.analytics

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.boxdev.boxwallet.ui.components.AmountKind
import com.boxdev.boxwallet.ui.components.AmountText
import com.boxdev.boxwallet.ui.components.ConnectedChoiceRow
import com.boxdev.boxwallet.ui.components.SectionHeader
import com.boxdev.boxwallet.ui.components.trendIcon
import com.boxdev.boxwallet.ui.components.charts.CategoryDonut
import com.boxdev.boxwallet.ui.components.charts.CashFlowChart
import com.boxdev.boxwallet.ui.components.charts.SpendingPatternChart
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.SearchAction
import com.boxdev.boxwallet.ui.components.TransactionItem
import androidx.compose.material.icons.outlined.InsertChart
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import com.boxdev.boxwallet.ui.theme.BoxTheme
import com.boxdev.boxwallet.ui.theme.isWide

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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onEditTransaction: (Long) -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }
    var showPicker by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val wide = BoxTheme.widthClass.isWide
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.analytics_title)) },
                subtitle = { Text(uiState.periodLabel) },
                actions = {
                    SearchAction(onClick = onNavigateToSearch)
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.analytics_export_report))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackBarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }

        val periodEmpty = uiState.transactionCount == 0

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
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
                    EmptyState(
                        icon = Icons.Outlined.InsertChart,
                        title = stringResource(R.string.analytics_empty_period_title),
                        message = stringResource(R.string.analytics_empty_period_msg),
                    )
                }
            }
        }
        val categories: @Composable () -> Unit = {
            if (uiState.categoryExpenses.isNotEmpty()) {
                ReportSection(stringResource(R.string.analytics_expenses_by_category)) {
                    CategoryDonut(uiState.categoryExpenses, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        val pattern: @Composable () -> Unit = {
            when {
                periodEmpty -> Unit
                uiState.frequency == AnalyticsFrequency.YEARLY -> ReportSection(stringResource(R.string.analytics_monthly_breakdown)) {
                    CashFlowChart(uiState.yearlyMonthlySummary, modifier = Modifier.fillMaxWidth())
                }
                uiState.dailySpending.any { it.amount > 0 } -> ReportSection(stringResource(R.string.analytics_spending_pattern)) {
                    SpendingPatternChart(
                        data = uiState.dailySpending,
                        average = uiState.avgDailyExpense,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val biggest = uiState.dailySpending.maxBy { it.amount }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                        StatTile(
                            label = stringResource(R.string.analytics_avg_per_day),
                            value = rememberCurrencyFormatter().format(uiState.avgDailyExpense),
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = stringResource(R.string.analytics_biggest_day),
                            value = rememberCurrencyFormatter().format(biggest.amount),
                            caption = biggest.date.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault()))
                                .replaceFirstChar { it.titlecase() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        val topExpenses: @Composable () -> Unit = {
            if (uiState.topExpenses.isNotEmpty()) {
                ReportSection(stringResource(R.string.analytics_top_expenses)) {
                    Column {
                        uiState.topExpenses.forEachIndexed { index, transaction ->
                            TransactionItem(
                                transaction = transaction,
                                category = transaction.categoryId?.let { uiState.categories[it] },
                                index = index,
                                count = uiState.topExpenses.size,
                                showDate = true,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                onClick = { onEditTransaction(transaction.id) },
                                modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                            )
                        }
                    }
                }
            }
        }
        val cashFlow: @Composable () -> Unit = {
            ReportSection(
                title = stringResource(R.string.analytics_cash_flow),
                subtitle = stringResource(R.string.analytics_last_12_months),
            ) {
                if (uiState.monthlyTrendData.any { it.income > 0 || it.expense > 0 }) {
                    CashFlowChart(data = uiState.monthlyTrendData, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 12.dp)) {
                        LegendItem(BoxTheme.money.income, stringResource(R.string.analytics_income_label))
                        LegendItem(BoxTheme.money.expense, stringResource(R.string.analytics_expense_label))
                    }
                } else NoDataPlaceholder()
            }
        }
        val health: @Composable () -> Unit = {
            uiState.financialMetrics?.let { metrics -> HealthSection(metrics) }
        }
        val insights: @Composable () -> Unit = {
            if (uiState.insights.isNotEmpty()) {
                ReportSection(stringResource(R.string.analytics_insights_title)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.insights.forEach { InsightItem(it) }
                    }
                }
            }
        }

        val scroll = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .verticalScroll(rememberScrollState())
        if (wide) {
            Column(scroll.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        controls(); summary(); emptyPeriod(); categories(); topExpenses()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        pattern(); cashFlow(); health(); insights()
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        } else {
            Column(scroll.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                controls(); summary(); emptyPeriod(); categories(); pattern(); topExpenses(); cashFlow(); health(); insights()
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showPicker) {
        when (uiState.frequency) {
            AnalyticsFrequency.DAILY, AnalyticsFrequency.WEEKLY -> DailyWeeklyDatePicker(
                frequency = uiState.frequency,
                periodOffset = uiState.periodOffset,
                onDismiss = { showPicker = false },
                onConfirm = { offset -> viewModel.setPeriodOffset(offset); showPicker = false }
            )
            AnalyticsFrequency.MONTHLY -> MonthYearPickerDialog(
                periodOffset = uiState.periodOffset,
                onDismiss = { showPicker = false },
                onConfirm = { offset -> viewModel.setPeriodOffset(offset); showPicker = false }
            )
            AnalyticsFrequency.YEARLY -> YearPickerDialog(
                periodOffset = uiState.periodOffset,
                onDismiss = { showPicker = false },
                onConfirm = { offset -> viewModel.setPeriodOffset(offset); showPicker = false }
            )
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

/** Net for the period, leading; money in and out beneath it with the change against the previous period. */
@Composable
private fun PeriodSummary(state: AnalyticsUiState) {
    val money = BoxTheme.money
    val net = state.periodIncome - state.periodExpense
    val onContainer = MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text(
                stringResource(R.string.analytics_net_savings),
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
            )
            AmountText(
                amount = kotlin.math.abs(net),
                kind = if (net >= 0) AmountKind.Income else AmountKind.Expense,
                style = BoxTheme.moneyType.hero.copy(fontSize = 44.sp, lineHeight = 48.sp),
                color = onContainer,
                autoSize = true,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowAmount(
                    label = stringResource(R.string.analytics_income_label),
                    amount = state.periodIncome, kind = AmountKind.Income,
                    container = money.incomeContainer, content = money.onIncomeContainer,
                    change = percentChange(state.periodIncome, state.previousIncome),
                    increaseIsGood = true,
                    modifier = Modifier.weight(1f),
                )
                FlowAmount(
                    label = stringResource(R.string.analytics_expense_label),
                    amount = state.periodExpense, kind = AmountKind.Expense,
                    container = money.expenseContainer, content = money.onExpenseContainer,
                    change = percentChange(state.periodExpense, state.previousExpense),
                    increaseIsGood = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SummaryStat(stringResource(R.string.analytics_movements), state.transactionCount.toString())
                if (state.frequency != AnalyticsFrequency.DAILY) {
                    SummaryStat(stringResource(R.string.analytics_avg_per_day), rememberCurrencyFormatter().format(state.avgDailyExpense))
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
    amount: Double,
    kind: AmountKind,
    container: Color,
    content: Color,
    change: Double?,
    increaseIsGood: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(trendIcon(kind == AmountKind.Income), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
            AmountText(amount, kind = kind, style = BoxTheme.moneyType.medium, color = content, autoSize = true)
            Spacer(Modifier.height(4.dp))
            if (change == null) {
                Text(
                    stringResource(R.string.analytics_no_previous),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.75f),
                    maxLines = 1,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (signedPercent(change) != "0.0%") {
                        Icon(
                            if (change > 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.analytics_vs_previous, "%.0f%%".format(kotlin.math.abs(change))),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Percent change from [previous] to [current]; null when there is nothing to compare against. */
private fun percentChange(current: Double, previous: Double): Double? =
    if (previous > 0) (current - previous) / previous * 100 else null

@Composable
private fun ReportSection(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
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

/** Savings rate as a ring, with the trend tiles beside it. */
@Composable
private fun HealthSection(metrics: FinancialMetrics) {
    val rate = metrics.savingsRate
    val ringColor = if (rate >= 0) BoxTheme.money.income else BoxTheme.money.expense
    ReportSection(stringResource(R.string.analytics_financial_health)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    Text(stringResource(R.string.analytics_savings_rate), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.analytics_savings_rate_caption),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = stringResource(R.string.analytics_daily_spending),
                    value = rememberCurrencyFormatter().format(metrics.avgDailySpending),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.analytics_income_trend),
                    value = signedPercent(metrics.incomeGrowth),
                    good = metrics.incomeGrowth.takeIf { signedPercent(it) != "0.0%" }?.let { it > 0 },
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.analytics_expense_trend),
                    value = signedPercent(metrics.expenseGrowth),
                    good = metrics.expenseGrowth.takeIf { signedPercent(it) != "0.0%" }?.let { it < 0 },
                    modifier = Modifier.weight(1f),
                )
            }
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

    var startDate by remember { mutableStateOf<LocalDate?>(today.minusMonths(1)) }
    var endDate by remember { mutableStateOf<LocalDate?>(today) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    if (showStartPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atOffset(ZoneOffset.UTC).toLocalDate().isAfter(today)
            }
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC).toLocalDate()
                        startDate = picked
                        if (endDate?.isBefore(picked) == true) endDate = picked
                    }
                    showStartPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showEndPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = Instant.ofEpochMilli(utcTimeMillis).atOffset(ZoneOffset.UTC).toLocalDate()
                    return !date.isAfter(today) && (startDate == null || !date.isBefore(startDate!!))
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        endDate = Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC).toLocalDate()
                    }
                    showEndPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) { DatePicker(state = pickerState) }
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
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = startDate?.format(dateFormatter) ?: "—",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.analytics_export_from)) },
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { showStartPicker = true })
                }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = endDate?.format(dateFormatter) ?: "—",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.analytics_export_to)) },
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(modifier = Modifier.matchParentSize().clickable { showEndPicker = true })
                }
            }

            Button(
                onClick = {
                    val s = startDate
                    val e = endDate
                    if (s != null && e != null) onGenerate(s, e)
                },
                enabled = startDate != null && endDate != null && !isExporting,
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
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay().toInstant(ZoneOffset.UTC)
            .toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date =
                    Instant.ofEpochMilli(utcTimeMillis).atOffset(ZoneOffset.UTC).toLocalDate()
                return !date.isAfter(today)
            }
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val selectedDate =
                        Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()
                    val offset = when (frequency) {
                        AnalyticsFrequency.DAILY -> ChronoUnit.DAYS.between(today, selectedDate)
                            .toInt()

                        else -> ChronoUnit.WEEKS.between(
                            today.with(DayOfWeek.MONDAY),
                            selectedDate.with(DayOfWeek.MONDAY)
                        ).toInt()
                    }
                    onConfirm(offset)
                }
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    ) { DatePicker(state = state) }
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
    val years = (currentYear downTo currentYear - 20).toList()
    val months = java.time.Month.entries

    var selectedMonth by remember { mutableStateOf(initialYearMonth.month) }
    var selectedYear by remember { mutableStateOf(initialYearMonth.year) }
    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }

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

                // Year dropdown
                ExposedDropdownMenuBox(
                    expanded = yearExpanded,
                    onExpandedChange = { yearExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedYear.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.analytics_year)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = yearExpanded,
                        onDismissRequest = { yearExpanded = false }
                    ) {
                        years.forEach { year ->
                            DropdownMenuItem(
                                text = { Text(year.toString()) },
                                onClick = {
                                    selectedYear = year
                                    // Clamp month if switching to current year
                                    if (year == currentYear && selectedMonth > currentYearMonth.month) {
                                        selectedMonth = currentYearMonth.month
                                    }
                                    yearExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selected = YearMonth.of(selectedYear, selectedMonth)
                val offset = ((selected.year - currentYearMonth.year) * 12 +
                        (selected.monthValue - currentYearMonth.monthValue))
                onConfirm(offset)
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YearPickerDialog(
    periodOffset: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val currentYear = LocalDate.now().year
    val initialYear = currentYear + periodOffset
    val years = (currentYear downTo currentYear - 20).toList()

    var selectedYear by remember { mutableStateOf(initialYear) }
    var yearExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.analytics_select_year)) },
        text = {
            ExposedDropdownMenuBox(
                expanded = yearExpanded,
                onExpandedChange = { yearExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedYear.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.analytics_year)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = yearExpanded,
                    onDismissRequest = { yearExpanded = false }
                ) {
                    years.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = {
                                selectedYear = year
                                yearExpanded = false
                            }
                        )
                    }
                }
            }
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
        FilledTonalIconButton(
            onClick = onBack,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(56.dp),
        ) {
            Icon(Icons.Default.ChevronLeft, contentDescription = stringResource(R.string.analytics_previous_period))
        }
        FilledTonalButton(
            onClick = onLabelClick,
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showToday) {
            FilledTonalIconButton(onClick = onToday, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Today, contentDescription = stringResource(R.string.analytics_today))
            }
        }
        FilledTonalIconButton(
            onClick = onForward,
            enabled = canGoForward,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(56.dp),
        ) {
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
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(width = 16.dp, height = 4.dp).background(color, CircleShape))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A small tonal tile: label, value, optional caption. [good] tints the value and adds a direction arrow. */
@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    good: Boolean? = null,
) {
    val valueColor = when (good) {
        true -> BoxTheme.money.income
        false -> BoxTheme.money.expense
        null -> MaterialTheme.colorScheme.onSurface
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                minLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (good != null) {
                    Icon(
                        if (value.startsWith("+")) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = valueColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    value,
                    style = BoxTheme.moneyType.small,
                    color = valueColor,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = BoxTheme.moneyType.small.fontSize),
                )
            }
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun InsightItem(insight: SpendingInsight) {
    val (icon, tint, container) = when (insight.type) {
        InsightType.POSITIVE -> Triple(Icons.Outlined.CheckCircle, BoxTheme.money.onIncomeContainer, BoxTheme.money.incomeContainer)
        InsightType.WARNING -> Triple(Icons.Outlined.WarningAmber, MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.tertiaryContainer)
        InsightType.NEUTRAL -> Triple(Icons.Outlined.Info, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
        InsightType.ALERT -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.onErrorContainer, MaterialTheme.colorScheme.errorContainer)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
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
