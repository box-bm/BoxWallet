@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.loans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.ui.components.LoanCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoansScreen(
    onNavigateToLoanDetail: (Long) -> Unit = {},
    viewModel: LoansViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Loans") },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        },
    ) { paddingValues ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { LoadingIndicator() }

            uiState.loans.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(bottom = 0.dp, top = paddingValues.calculateTopPadding()),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text("No loans yet", style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Tap + to add a loan", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(bottom = 0.dp, top = paddingValues.calculateTopPadding()),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    AnimatedVisibility(visible = showFilters) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text("Status", style = MaterialTheme.typography.titleSmall)
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = uiState.filterState.statusFilter == LoanStatusFilter.ALL,
                                        onClick = { viewModel.updateStatusFilter(LoanStatusFilter.ALL) },
                                        label = { Text("All") },
                                        shape = SegmentedButtonDefaults.itemShape(0, 3)
                                    )
                                    SegmentedButton(
                                        selected = uiState.filterState.statusFilter == LoanStatusFilter.CURRENT,
                                        onClick = { viewModel.updateStatusFilter(LoanStatusFilter.CURRENT) },
                                        label = { Text("Current") },
                                        shape = SegmentedButtonDefaults.itemShape(1, 3)
                                    )
                                    SegmentedButton(
                                        selected = uiState.filterState.statusFilter == LoanStatusFilter.CLOSED,
                                        onClick = { viewModel.updateStatusFilter(LoanStatusFilter.CLOSED) },
                                        label = { Text("Closed") },
                                        shape = SegmentedButtonDefaults.itemShape(2, 3)
                                    )
                                }

                                Text("Sort By", style = MaterialTheme.typography.titleSmall)
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = uiState.filterState.sortBy == LoanSortBy.DATE_DESC,
                                        onClick = { viewModel.updateSortBy(LoanSortBy.DATE_DESC) },
                                        label = { Text("Latest") },
                                        shape = SegmentedButtonDefaults.itemShape(0, 4)
                                    )
                                    SegmentedButton(
                                        selected = uiState.filterState.sortBy == LoanSortBy.DATE_ASC,
                                        onClick = { viewModel.updateSortBy(LoanSortBy.DATE_ASC) },
                                        label = { Text("Oldest") },
                                        shape = SegmentedButtonDefaults.itemShape(1, 4)
                                    )
                                    SegmentedButton(
                                        selected = uiState.filterState.sortBy == LoanSortBy.AMOUNT_DESC,
                                        onClick = { viewModel.updateSortBy(LoanSortBy.AMOUNT_DESC) },
                                        label = { Text("Highest") },
                                        shape = SegmentedButtonDefaults.itemShape(2, 4)
                                    )
                                    SegmentedButton(
                                        selected = uiState.filterState.sortBy == LoanSortBy.AMOUNT_ASC,
                                        onClick = { viewModel.updateSortBy(LoanSortBy.AMOUNT_ASC) },
                                        label = { Text("Lowest") },
                                        shape = SegmentedButtonDefaults.itemShape(3, 4)
                                    )
                                }

                                TextButton(
                                    onClick = { viewModel.clearFilters() },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Clear All")
                                }
                            }
                        }
                    }
                }

                items(uiState.filteredLoans, key = { it.id }) { loan ->
                    LoanCard(
                        loan = loan,
                        onClick = { onNavigateToLoanDetail(loan.id) }
                    )
                }
            }
        }
    }
}
