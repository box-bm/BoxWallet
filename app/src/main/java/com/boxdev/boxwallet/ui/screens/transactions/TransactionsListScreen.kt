@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.ui.components.TransactionItem
import com.boxdev.boxwallet.ui.components.TransactionSheet
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TransactionsListScreen(
    viewModel: TransactionsListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val categoryMap = remember(uiState.availableCategories) { uiState.availableCategories.associateBy { it.id } }
    var showFilters by remember { mutableStateOf(false) }
    var showTransactionSheet by remember { mutableStateOf(false) }
    var selectedTransactionId by remember { mutableStateOf<Long?>(null) }

    val handleCloseTransactionSheet = { showTransactionSheet = false }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { selectedTransactionId = null; showTransactionSheet = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = uiState.filterState.searchQuery,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text("Search transactions…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            Row {
                                if (uiState.filterState.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                                IconButton(onClick = { showFilters = !showFilters }) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = "Filter",
                                        tint = if (showFilters) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                }
                            }
                        }
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            ) {}

            AnimatedVisibility(visible = showFilters) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { viewModel.clearFilters() }) { Text("Clear All") }
                        }

                        Text("Type", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.filterState.transactionType == TransactionTypeFilter.ALL,
                                onClick = { viewModel.updateTypeFilter(TransactionTypeFilter.ALL) },
                                label = { Text("All") }
                            )
                            FilterChip(
                                selected = uiState.filterState.transactionType == TransactionTypeFilter.INCOME,
                                onClick = { viewModel.updateTypeFilter(TransactionTypeFilter.INCOME) },
                                label = { Text("Income") }
                            )
                            FilterChip(
                                selected = uiState.filterState.transactionType == TransactionTypeFilter.EXPENSE,
                                onClick = { viewModel.updateTypeFilter(TransactionTypeFilter.EXPENSE) },
                                label = { Text("Expense") }
                            )
                        }

                        Text("Sort", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.filterState.sortBy == SortBy.DATE_DESC,
                                onClick = { viewModel.updateSortBy(SortBy.DATE_DESC) },
                                label = { Text("Latest") }
                            )
                            FilterChip(
                                selected = uiState.filterState.sortBy == SortBy.DATE_ASC,
                                onClick = { viewModel.updateSortBy(SortBy.DATE_ASC) },
                                label = { Text("Oldest") }
                            )
                            FilterChip(
                                selected = uiState.filterState.sortBy == SortBy.AMOUNT_DESC,
                                onClick = { viewModel.updateSortBy(SortBy.AMOUNT_DESC) },
                                label = { Text("Highest") }
                            )
                        }
                    }
                }
            }

            when {
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                uiState.filteredTransactions.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (uiState.transactions.isEmpty()) "No transactions yet" else "No transactions found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    val grouped = uiState.filteredTransactions.groupBy { it.date }.entries.sortedByDescending { it.key }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        grouped.forEach { (date, transactions) ->
                            stickyHeader(key = date.toString()) {
                                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                                    Text(
                                        text = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                            items(transactions, key = { it.id }) { transaction ->
                                TransactionItem(
                                    transaction = transaction,
                                    category = transaction.categoryId?.let { categoryMap[it] },
                                    onClick = { selectedTransactionId = transaction.id; showTransactionSheet = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTransactionSheet) {
        TransactionSheet(
            transactionId = selectedTransactionId,
            onDismiss = handleCloseTransactionSheet
        )
    }
}
