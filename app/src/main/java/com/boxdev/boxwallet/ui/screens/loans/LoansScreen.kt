@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.loans

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.LoanCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoansScreen(
    onNavigateToLoanDetail: (Long) -> Unit = {},
    viewModel: LoansViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    val scrollBehavior =
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(bottom = 0),
        topBar = {
            MediumTopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(R.string.loans_title)) },
                actions = {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.action_filter))
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
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Payments,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(stringResource(R.string.loans_no_loans), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.loans_tap_to_add), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
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
                                Text(stringResource(R.string.loans_filter_status), style = MaterialTheme.typography.titleSmall)
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = uiState.filterState.statusFilter == LoanStatusFilter.ALL,
                                        onClick = { viewModel.updateStatusFilter(LoanStatusFilter.ALL) },
                                        label = { Text(stringResource(R.string.transactions_filter_all)) },
                                        shape = SegmentedButtonDefaults.itemShape(0, 3)
                                    )
                                    SegmentedButton(
                                        selected = uiState.filterState.statusFilter == LoanStatusFilter.CURRENT,
                                        onClick = { viewModel.updateStatusFilter(LoanStatusFilter.CURRENT) },
                                        label = { Text(stringResource(R.string.loans_filter_current)) },
                                        shape = SegmentedButtonDefaults.itemShape(1, 3)
                                    )
                                    SegmentedButton(
                                        selected = uiState.filterState.statusFilter == LoanStatusFilter.CLOSED,
                                        onClick = { viewModel.updateStatusFilter(LoanStatusFilter.CLOSED) },
                                        label = { Text(stringResource(R.string.loans_filter_closed)) },
                                        shape = SegmentedButtonDefaults.itemShape(2, 3)
                                    )
                                }

                                Text(stringResource(R.string.loans_filter_sort_by), style = MaterialTheme.typography.titleSmall)
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = uiState.filterState.sortBy == LoanSortBy.DATE_DESC,
                                        onClick = { viewModel.updateSortBy(LoanSortBy.DATE_DESC) },
                                        label = { Text(stringResource(R.string.transactions_filter_latest)) },
                                        shape = SegmentedButtonDefaults.itemShape(0, 4)
                                    )
                                    SegmentedButton(
                                        selected = uiState.filterState.sortBy == LoanSortBy.DATE_ASC,
                                        onClick = { viewModel.updateSortBy(LoanSortBy.DATE_ASC) },
                                        label = { Text(stringResource(R.string.transactions_filter_oldest)) },
                                        shape = SegmentedButtonDefaults.itemShape(1, 4)
                                    )
                                    SegmentedButton(
                                        selected = uiState.filterState.sortBy == LoanSortBy.AMOUNT_DESC,
                                        onClick = { viewModel.updateSortBy(LoanSortBy.AMOUNT_DESC) },
                                        label = { Text(stringResource(R.string.transactions_filter_highest)) },
                                        shape = SegmentedButtonDefaults.itemShape(2, 4)
                                    )
                                    SegmentedButton(
                                        selected = uiState.filterState.sortBy == LoanSortBy.AMOUNT_ASC,
                                        onClick = { viewModel.updateSortBy(LoanSortBy.AMOUNT_ASC) },
                                        label = { Text(stringResource(R.string.loans_filter_lowest)) },
                                        shape = SegmentedButtonDefaults.itemShape(3, 4)
                                    )
                                }

                                TextButton(
                                    onClick = { viewModel.clearFilters() },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(stringResource(R.string.action_clear_all))
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

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}
