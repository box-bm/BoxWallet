package com.boxdev.boxwallet.ui.screens.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.core.axis.AxisItemPlacer
import com.patrykandpatrick.vico.core.chart.column.ColumnChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault())
    val monthFormatter = DateTimeFormatter.ofPattern("MMM")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Analytics",
                    )
                },
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Period Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(3, 6, 12).forEach { months ->
                        FilterChip(
                            selected = uiState.selectedPeriod == months,
                            onClick = { viewModel.updatePeriod(months) },
                            label = { Text("$months months") }
                        )
                    }
                }

                // Chart Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Income vs Expenses",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.monthlyData.isNotEmpty()) {
                            val incomeEntries = uiState.monthlyData.mapIndexed { index, data ->
                                entryOf(index.toFloat(), data.income.toFloat())
                            }
                            val expenseEntries = uiState.monthlyData.mapIndexed { index, data ->
                                entryOf(index.toFloat(), data.expense.toFloat())
                            }

                            val chartEntryModelProducer = remember(incomeEntries, expenseEntries) {
                                ChartEntryModelProducer(listOf(incomeEntries, expenseEntries))
                            }

                            val columnChart = columnChart(
                                columns = listOf(
                                    lineComponent(
                                        color = MaterialTheme.colorScheme.primary,
                                        thickness = 16.dp
                                    ),
                                    lineComponent(
                                        color = MaterialTheme.colorScheme.error,
                                        thickness = 16.dp
                                    )
                                ),
                                mergeMode = ColumnChart.MergeMode.Grouped,
                                spacing = 8.dp
                            )

                            Chart(
                                chart = columnChart,
                                chartModelProducer = chartEntryModelProducer,
                                startAxis = rememberStartAxis(
                                    itemPlacer = AxisItemPlacer.Vertical.default(maxItemCount = 5)
                                ),
                                bottomAxis = rememberBottomAxis(
                                    valueFormatter = { value, _ ->
                                        uiState.monthlyData.getOrNull(value.toInt())?.month?.format(monthFormatter) ?: ""
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(250.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Legend
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                LegendItem(color = MaterialTheme.colorScheme.primary, label = "Income")
                                LegendItem(color = MaterialTheme.colorScheme.error, label = "Expense")
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No data available",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Summary Cards
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Period Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        val totalIncome = uiState.monthlyData.sumOf { it.income }
                        val totalExpense = uiState.monthlyData.sumOf { it.expense }
                        val avgIncome = if (uiState.monthlyData.isNotEmpty()) totalIncome / uiState.monthlyData.size else 0.0
                        val avgExpense = if (uiState.monthlyData.isNotEmpty()) totalExpense / uiState.monthlyData.size else 0.0

                        SummaryRow("Total Income", currencyFormat.format(totalIncome), MaterialTheme.colorScheme.primary)
                        SummaryRow("Total Expense", currencyFormat.format(totalExpense), MaterialTheme.colorScheme.error)
                        SummaryRow("Avg. Monthly Income", currencyFormat.format(avgIncome), MaterialTheme.colorScheme.primary)
                        SummaryRow("Avg. Monthly Expense", currencyFormat.format(avgExpense), MaterialTheme.colorScheme.error)
                        SummaryRow(
                            "Net Savings",
                            currencyFormat.format(totalIncome - totalExpense),
                            if (totalIncome >= totalExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                // Monthly Breakdown
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Monthly Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        uiState.monthlyData.reversed().forEach { data ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = data.month.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "+${currencyFormat.format(data.income)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "-${currencyFormat.format(data.expense)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.height(16.dp),
            colors = CardDefaults.cardColors(containerColor = color)
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}
