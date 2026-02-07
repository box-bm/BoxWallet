package com.boxdev.boxwallet.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.boxdev.boxwallet.ui.components.SummaryCard
import com.boxdev.boxwallet.ui.components.TransactionItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTransaction: (Long?) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        floatingActionButton = {

                AnimatedVisibility(
                    visible = !uiState.isSelectionMode,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { onNavigateToTransaction(null) },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add transaction"
                            )
                        },
                        text = { Text("Add") },
                    )
            }
        },
        topBar = {
            MediumTopAppBar(
                title = {
                    if (uiState.isSelectionMode) {
                        Text("${uiState.selectedTransactions.size} selected")
                    } else {
                        Text(
                            text = "BoxWallet",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear selection"
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { viewModel.deleteSelectedTransactions() }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete selected"
                            )
                        }
                    }
                }
            )
        },
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary Card
                item {
                    SummaryCard(summary = uiState.financialSummary)
                }

                // Latest Expenses Section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(
                        title = "Latest Expenses",
                        count = uiState.latestExpenses.size
                    )
                }

                if (uiState.latestExpenses.isEmpty()) {
                    item {
                        EmptyStateCard(message = "No expenses recorded yet")
                    }
                } else {
                    items(
                        items = uiState.latestExpenses,
                        key = { it.id }
                    ) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            isSelected = uiState.selectedTransactions.contains(transaction.id),
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleTransactionSelection(transaction.id)
                                } else {
                                    onNavigateToTransaction(transaction.id)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleTransactionSelection(transaction.id)
                            }
                        )
                    }
                }

                // Recent Incomes Section
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(
                        title = "Recent Incomes",
                        count = uiState.latestIncomes.size
                    )
                }

                if (uiState.latestIncomes.isEmpty()) {
                    item {
                        EmptyStateCard(message = "No incomes recorded yet")
                    }
                } else {
                    items(
                        items = uiState.latestIncomes,
                        key = { it.id }
                    ) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            isSelected = uiState.selectedTransactions.contains(transaction.id),
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleTransactionSelection(transaction.id)
                                } else {
                                    onNavigateToTransaction(transaction.id)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleTransactionSelection(transaction.id)
                            }
                        )
                    }
                }

                // Bottom spacing for FAB
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "$count items",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EmptyStateCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
