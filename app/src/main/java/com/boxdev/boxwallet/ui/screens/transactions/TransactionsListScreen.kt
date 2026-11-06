@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)

package com.boxdev.boxwallet.ui.screens.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.TransactionItem
import java.time.format.DateTimeFormatter

@Composable
fun TransactionsListScreen(
    onEditTransaction: (Long) -> Unit = {},
    viewModel: TransactionsListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val categoryMap = remember(uiState.availableCategories) {
        uiState.availableCategories.associateBy { it.id }
    }
    var showFilters by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            TextField(
                value = uiState.filterState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.transactions_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        if (uiState.filterState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.action_clear)
                                )
                            }
                        }
                        IconButton(onClick = { showFilters = !showFilters }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.action_filter),
                                tint = if (showFilters) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                },
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp)
            )

            // Filter panel
            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Type
                    Text(
                        stringResource(R.string.transactions_filter_type),
                        style = MaterialTheme.typography.titleSmall
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.filterState.transactionType == TransactionTypeFilter.ALL,
                            onClick = { viewModel.updateTypeFilter(TransactionTypeFilter.ALL) },
                            label = { Text(stringResource(R.string.transactions_filter_all)) },
                            shape = SegmentedButtonDefaults.itemShape(0, 3)
                        )
                        SegmentedButton(
                            selected = uiState.filterState.transactionType == TransactionTypeFilter.INCOME,
                            onClick = { viewModel.updateTypeFilter(TransactionTypeFilter.INCOME) },
                            label = { Text(stringResource(R.string.transaction_type_income)) },
                            shape = SegmentedButtonDefaults.itemShape(1, 3)
                        )
                        SegmentedButton(
                            selected = uiState.filterState.transactionType == TransactionTypeFilter.EXPENSE,
                            onClick = { viewModel.updateTypeFilter(TransactionTypeFilter.EXPENSE) },
                            label = { Text(stringResource(R.string.transaction_type_expense)) },
                            shape = SegmentedButtonDefaults.itemShape(2, 3)
                        )
                    }

                    // Category
                    if (uiState.availableCategories.isNotEmpty()) {
                        Text(
                            stringResource(R.string.transactions_filter_category),
                            style = MaterialTheme.typography.titleSmall
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.filterState.categoryId == null,
                                onClick = { viewModel.updateCategoryFilter(null) },
                                label = { Text(stringResource(R.string.transactions_filter_all)) }
                            )
                            uiState.availableCategories.forEach { cat ->
                                FilterChip(
                                    selected = uiState.filterState.categoryId == cat.id,
                                    onClick = { viewModel.updateCategoryFilter(cat.id) },
                                    label = { Text(cat.name) }
                                )
                            }
                        }
                    }

                    // Sort
                    Text(
                        stringResource(R.string.transactions_filter_sort),
                        style = MaterialTheme.typography.titleSmall
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = uiState.filterState.sortBy == SortBy.DATE_DESC,
                            onClick = { viewModel.updateSortBy(SortBy.DATE_DESC) },
                            label = { Text(stringResource(R.string.transactions_filter_latest)) },
                            shape = SegmentedButtonDefaults.itemShape(0, 4)
                        )
                        SegmentedButton(
                            selected = uiState.filterState.sortBy == SortBy.DATE_ASC,
                            onClick = { viewModel.updateSortBy(SortBy.DATE_ASC) },
                            label = { Text(stringResource(R.string.transactions_filter_oldest)) },
                            shape = SegmentedButtonDefaults.itemShape(1, 4)
                        )
                        SegmentedButton(
                            selected = uiState.filterState.sortBy == SortBy.AMOUNT_DESC,
                            onClick = { viewModel.updateSortBy(SortBy.AMOUNT_DESC) },
                            label = { Text(stringResource(R.string.transactions_filter_highest)) },
                            shape = SegmentedButtonDefaults.itemShape(2, 4)
                        )
                        SegmentedButton(
                            selected = uiState.filterState.sortBy == SortBy.AMOUNT_ASC,
                            onClick = { viewModel.updateSortBy(SortBy.AMOUNT_ASC) },
                            label = { Text(stringResource(R.string.transactions_filter_lowest)) },
                            shape = SegmentedButtonDefaults.itemShape(3, 4)
                        )
                    }

                    // Recurring
                    FilterChip(
                        selected = uiState.filterState.showOnlyRecurrent,
                        onClick = { viewModel.updateRecurrentFilter(!uiState.filterState.showOnlyRecurrent) },
                        label = { Text(stringResource(R.string.transactions_filter_recurring_only)) }
                    )

                    TextButton(
                        onClick = { viewModel.clearFilters() },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.action_clear_all))
                    }
                }
            }

            // Content
            when {
                uiState.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { LoadingIndicator() }

                uiState.filteredTransactions.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.transactions.isEmpty())
                            stringResource(R.string.transactions_no_transactions)
                        else
                            stringResource(R.string.transactions_no_results),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    val grouped = remember(uiState.filteredTransactions) {
                        uiState.filteredTransactions
                            .groupBy { it.date.toLocalDate() }
                            .entries
                            .sortedByDescending { it.key }
                    }
                    val headerFormatter = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d") }
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
                                        text = date.format(headerFormatter),
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
                                    onClick = { onEditTransaction(transaction.id) }
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(84.dp)) }
                    }
                }
            }
        }
    }
}
