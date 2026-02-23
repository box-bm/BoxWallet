@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.analytics

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.charts.CategoryBarChart
import com.boxdev.boxwallet.ui.components.charts.IncomeExpensePieChart
import com.boxdev.boxwallet.ui.components.charts.MonthlyTrendChart
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
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }
    var showPicker by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val formatter = rememberCurrencyFormatter()

    LaunchedEffect(uiState.exportError) {
        uiState.exportError?.let {
            snackBarHostState.showSnackbar(it)
            viewModel.clearExportState()
        }
    }

    LaunchedEffect(uiState.exportFile) {
        uiState.exportFile?.let { file ->
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(
                    shareIntent,
                    context.getString(R.string.analytics_export_share)
                )
            )
            viewModel.clearExportState()
            showExportSheet = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analytics_title)) },
                actions = {
                    IconButton(onClick = { showExportSheet = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = stringResource(R.string.analytics_export_report)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackBarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Frequency Selector
                FrequencySelector(
                    selected = uiState.frequency,
                    onSelected = { viewModel.updateFrequency(it) }
                )

                // Period Navigator — ButtonGroup
                PeriodNavigator(
                    label = uiState.periodLabel,
                    canGoForward = uiState.periodOffset < 0,
                    onBack = { viewModel.navigatePeriod(-1) },
                    onForward = { viewModel.navigatePeriod(1) },
                    onLabelClick = { showPicker = true }
                )

                if (uiState.periodOffset != 0) {
                    TextButton(
                        onClick = { viewModel.setPeriodOffset(0) },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.analytics_today))
                    }
                }

                // Income/Expense Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            stringResource(R.string.analytics_income_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatter.format(uiState.periodIncome),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            stringResource(R.string.analytics_expense_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatter.format(uiState.periodExpense),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                val net = uiState.periodIncome - uiState.periodExpense
                Text(
                    "${stringResource(R.string.analytics_net_savings)}: ${formatter.format(net)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (net >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )

                HorizontalDivider()

                // Yearly Monthly Summary (only when YEARLY)
                if (uiState.frequency == AnalyticsFrequency.YEARLY && uiState.yearlyMonthlySummary.isNotEmpty()) {
                    Text(
                        stringResource(R.string.analytics_monthly_breakdown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.yearlyMonthlySummary.forEach { data ->
                            if (data.income > 0 || data.expense > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = data.month.month.getDisplayName(
                                            TextStyle.SHORT,
                                            Locale.getDefault()
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(48.dp)
                                    )
                                    Text(
                                        text = formatter.format(data.income),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = formatter.format(data.expense),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }

                // Income vs Expense Pie Chart
                Text(
                    stringResource(R.string.analytics_income_vs_expense),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (uiState.periodIncome > 0 || uiState.periodExpense > 0) {
                    IncomeExpensePieChart(
                        income = uiState.periodIncome,
                        expense = uiState.periodExpense,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendItem(
                            MaterialTheme.colorScheme.primary,
                            stringResource(R.string.analytics_income_label)
                        )
                        LegendItem(
                            MaterialTheme.colorScheme.error,
                            stringResource(R.string.analytics_expense_label)
                        )
                    }
                } else {
                    NoDataPlaceholder()
                }

                HorizontalDivider()

                // Category Expenses Bar Chart
                Text(
                    stringResource(R.string.analytics_expenses_by_category),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (uiState.categoryExpenses.isNotEmpty()) {
                    CategoryBarChart(
                        data = uiState.categoryExpenses,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    NoDataPlaceholder()
                }

                HorizontalDivider()

                // Monthly Trend Chart
                Text(
                    stringResource(R.string.analytics_monthly_trend),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (uiState.monthlyTrendData.any { it.income > 0 || it.expense > 0 }) {
                    MonthlyTrendChart(
                        data = uiState.monthlyTrendData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendItem(
                            MaterialTheme.colorScheme.primary,
                            stringResource(R.string.analytics_income_label)
                        )
                        LegendItem(
                            MaterialTheme.colorScheme.error,
                            stringResource(R.string.analytics_expense_label)
                        )
                    }
                } else {
                    NoDataPlaceholder()
                }

                HorizontalDivider()

                // Financial Health Metrics
                uiState.financialMetrics?.let { metrics ->
                    Text(
                        stringResource(R.string.analytics_financial_health),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem(
                            title = stringResource(R.string.analytics_savings_rate),
                            value = "${String.format("%.1f", metrics.savingsRate)}%",
                            modifier = Modifier.weight(1f)
                        )
                        MetricItem(
                            title = stringResource(R.string.analytics_daily_spending),
                            value = formatter.format(metrics.avgDailySpending),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricItem(
                            title = stringResource(R.string.analytics_income_trend),
                            value = "${if (metrics.incomeGrowth >= 0) "+" else ""}${
                                String.format(
                                    "%.1f",
                                    metrics.incomeGrowth
                                )
                            }%",
                            modifier = Modifier.weight(1f),
                            valueColor = if (metrics.incomeGrowth >= 0) Color(0xFF4CAF50) else Color(
                                0xFFF44336
                            )
                        )
                        MetricItem(
                            title = stringResource(R.string.analytics_expense_trend),
                            value = "${if (metrics.expenseGrowth >= 0) "+" else ""}${
                                String.format(
                                    "%.1f",
                                    metrics.expenseGrowth
                                )
                            }%",
                            modifier = Modifier.weight(1f),
                            valueColor = if (metrics.expenseGrowth <= 0) Color(0xFF4CAF50) else Color(
                                0xFFF44336
                            )
                        )
                    }
                }

                // Spending Insights
                if (uiState.insights.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.analytics_insights_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    uiState.insights.forEach { insight ->
                        InsightItem(insight)
                    }
                }
            }
        }
    }

    if (showPicker) {
        when (uiState.frequency) {
            AnalyticsFrequency.DAILY, AnalyticsFrequency.WEEKLY -> {
                DailyWeeklyDatePicker(
                    frequency = uiState.frequency,
                    periodOffset = uiState.periodOffset,
                    onDismiss = { showPicker = false },
                    onConfirm = { offset ->
                        viewModel.setPeriodOffset(offset)
                        showPicker = false
                    }
                )
            }

            AnalyticsFrequency.MONTHLY -> {
                MonthYearPickerDialog(
                    periodOffset = uiState.periodOffset,
                    onDismiss = { showPicker = false },
                    onConfirm = { offset ->
                        viewModel.setPeriodOffset(offset)
                        showPicker = false
                    }
                )
            }

            AnalyticsFrequency.YEARLY -> {
                YearPickerDialog(
                    periodOffset = uiState.periodOffset,
                    onDismiss = { showPicker = false },
                    onConfirm = { offset ->
                        viewModel.setPeriodOffset(offset)
                        showPicker = false
                    }
                )
            }
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
private fun FrequencySelector(
    selected: AnalyticsFrequency,
    onSelected: (AnalyticsFrequency) -> Unit
) {
    val options = AnalyticsFrequency.entries
    val labels = mapOf(
        AnalyticsFrequency.DAILY to stringResource(R.string.analytics_frequency_daily),
        AnalyticsFrequency.WEEKLY to stringResource(R.string.analytics_frequency_weekly),
        AnalyticsFrequency.MONTHLY to stringResource(R.string.analytics_frequency_monthly),
        AnalyticsFrequency.YEARLY to stringResource(R.string.analytics_frequency_yearly)
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, freq ->
            SegmentedButton(
                selected = selected == freq,
                onClick = { onSelected(freq) },
                label = { Text(labels[freq] ?: freq.name) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size)
            )
        }
    }
}

@Composable
private fun PeriodNavigator(
    label: String,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onLabelClick: () -> Unit = {}
) {
    @Suppress("DEPRECATION")
    ButtonGroup(
        modifier = Modifier.fillMaxWidth()
    ) {
        FilledTonalButton(
            onClick = onBack,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.action_back)
            )
        }
        FilledTonalButton(
            onClick = onLabelClick,
            modifier = Modifier.weight(3f)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        FilledTonalButton(
            onClick = onForward,
            enabled = canGoForward,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun NoDataPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.analytics_no_data),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MetricItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun InsightItem(insight: SpendingInsight) {
    val (icon, iconColor) = when (insight.type) {
        InsightType.POSITIVE -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
        InsightType.WARNING -> Icons.Default.Warning to Color(0xFFFF9800)
        InsightType.NEUTRAL -> Icons.Default.Info to MaterialTheme.colorScheme.primary
        InsightType.ALERT -> Icons.Default.Error to Color(0xFFF44336)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = insight.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
