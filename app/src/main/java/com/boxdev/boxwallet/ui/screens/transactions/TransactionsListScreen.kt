@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.boxdev.boxwallet.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onNavigateBack: () -> Unit,
    viewModel: TransactionsListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val categoryMap =
        remember(uiState.availableCategories) { uiState.availableCategories.associateBy { it.id } }
    var showFilters by remember { mutableStateOf(false) }
    var showTransactionSheet by remember { mutableStateOf(false) }
    var selectedTransactionId by remember { mutableStateOf<Long?>(null) }

    val handleCloseTransactionSheet = { showTransactionSheet = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                selectedTransactionId = null; showTransactionSheet = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {}

            AnimatedVisibility(visible = showFilters) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Filters",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { viewModel.clearFilters() }) { Text("Clear All") }
                        }

                        Column() {
                            Text("Type", style = MaterialTheme.typography.labelMedium)
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SegmentedButton(
                                    selected = uiState.filterState.transactionType == TransactionTypeFilter.ALL,
                                    onClick = { viewModel.updateTypeFilter(TransactionTypeFilter.ALL) },
                                    label = { Text("All") },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                                )
                                SegmentedButton(
                                    selected = uiState.filterState.transactionType == TransactionTypeFilter.INCOME,
                                    onClick = { viewModel.updateTypeFilter(TransactionTypeFilter.INCOME) },
                                    label = { Text("Income") },
                                    shape = SegmentedButtonDefaults.itemShape(1, 3)
                                )
                                SegmentedButton(
                                    selected = uiState.filterState.transactionType == TransactionTypeFilter.EXPENSE,
                                    onClick = { viewModel.updateTypeFilter(TransactionTypeFilter.EXPENSE) },
                                    label = { Text("Expense") },
                                    shape = SegmentedButtonDefaults.itemShape(2, 3)
                                )
                            }
                        }

                        Column {
                            Text("Sort", style = MaterialTheme.typography.labelMedium)
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SegmentedButton(
                                    selected = uiState.filterState.sortBy == SortBy.DATE_DESC,
                                    onClick = { viewModel.updateSortBy(SortBy.DATE_DESC) },
                                    label = { Text("Latest") },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                                )
                                SegmentedButton(
                                    selected = uiState.filterState.sortBy == SortBy.DATE_ASC,
                                    onClick = { viewModel.updateSortBy(SortBy.DATE_ASC) },
                                    label = { Text("Oldest") },
                                    shape = SegmentedButtonDefaults.itemShape(1, 3)
                                )
                                SegmentedButton(
                                    selected = uiState.filterState.sortBy == SortBy.AMOUNT_DESC,
                                    onClick = { viewModel.updateSortBy(SortBy.AMOUNT_DESC) },
                                    label = { Text("Highest") },
                                    shape = SegmentedButtonDefaults.itemShape(2, 3)
                                )
                            }
                        }

                        FilterChip(
                            selected = uiState.filterState.showOnlyRecurrent,
                            onClick = { viewModel.updateRecurrentFilter(!uiState.filterState.showOnlyRecurrent) },
                            label = { Text("Recurring Only") }
                        )
                    }
                }
            }

            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }

                uiState.filteredTransactions.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.transactions.isEmpty()) "No transactions yet" else "No transactions found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    val grouped =
                        uiState.filteredTransactions.groupBy { it.date }.entries.sortedByDescending { it.key }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        grouped.forEach { (date, transactions) ->
                            stickyHeader(key = date.toString()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
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
                                    onClick = {
                                        selectedTransactionId =
                                            transaction.id; showTransactionSheet = true
                                    }
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
