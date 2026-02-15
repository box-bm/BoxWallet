@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.charts.BalanceTrendChart
import com.boxdev.boxwallet.ui.components.charts.CategoryDonutChart
import com.boxdev.boxwallet.ui.components.charts.ProjectionLineChart
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analytics_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                LoadingIndicator()
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 6, 12).forEach { months ->
                        FilterChip(
                            selected = uiState.selectedPeriod == months,
                            onClick = { viewModel.updatePeriod(months) },
                            label = { Text(stringResource(R.string.analytics_months, months)) }
                        )
                    }
                }

                // Expenses by Category
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.analytics_expenses_by_category),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.categoryBreakdown.isNotEmpty()) {
                            CategoryDonutChart(
                                data = uiState.categoryBreakdown,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.categoryBreakdown.forEach { category ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .background(category.color, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "${category.categoryName}: ${"%.1f".format(category.percentage)}%",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.analytics_no_category_data),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.analytics_period_summary), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val totalIncome = uiState.monthlyData.sumOf { it.income }
                        val totalExpense = uiState.monthlyData.sumOf { it.expense }
                        val avgIncome = if (uiState.monthlyData.isNotEmpty()) totalIncome / uiState.monthlyData.size else 0.0
                        val avgExpense = if (uiState.monthlyData.isNotEmpty()) totalExpense / uiState.monthlyData.size else 0.0

                        SummaryRow(stringResource(R.string.analytics_total_income), "%.2f".format(totalIncome), MaterialTheme.colorScheme.primary)
                        SummaryRow(stringResource(R.string.analytics_total_expense), "%.2f".format(totalExpense), MaterialTheme.colorScheme.error)
                        SummaryRow(stringResource(R.string.analytics_avg_monthly_income), "%.2f".format(avgIncome), MaterialTheme.colorScheme.primary)
                        SummaryRow(stringResource(R.string.analytics_avg_monthly_expense), "%.2f".format(avgExpense), MaterialTheme.colorScheme.error)
                        SummaryRow(
                            stringResource(R.string.analytics_net_savings),
                            "%.2f".format(totalIncome - totalExpense),
                            if (totalIncome >= totalExpense) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.analytics_monthly_breakdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        uiState.monthlyData.reversed().forEach { data ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    text = data.month.format(DateTimeFormatter.ofPattern("MMM yyyy")),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("+${"%.2f".format(data.income)}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary)
                                    Text("-${"%.2f".format(data.expense)}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // Expense Projection
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.analytics_expense_projection),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.projectionData.isNotEmpty()) {
                            ProjectionLineChart(
                                data = uiState.projectionData,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.analytics_insufficient_data),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Balance Trend
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.analytics_balance_trend),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (uiState.balanceTrend.isNotEmpty()) {
                            BalanceTrendChart(
                                data = uiState.balanceTrend,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )

                            val latestTrend = uiState.balanceTrend.lastOrNull()
                            latestTrend?.let {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (it.isIncreasing) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                        contentDescription = null,
                                        tint = if (it.isIncreasing) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (it.isIncreasing) stringResource(R.string.analytics_balance_increasing) else stringResource(R.string.analytics_balance_decreasing),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (it.isIncreasing) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.analytics_no_trend_data),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(modifier = Modifier.height(16.dp), colors = CardDefaults.cardColors(containerColor = color)) {
            Box(modifier = Modifier.padding(horizontal = 12.dp))
        }
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = valueColor)
    }
}
