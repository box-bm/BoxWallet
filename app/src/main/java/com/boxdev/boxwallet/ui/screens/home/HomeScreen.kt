@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.data.previewParams.HomeUiStatePreviewParameterProvider
import com.boxdev.boxwallet.ui.components.LoanCard
import com.boxdev.boxwallet.ui.components.LoanSheet
import com.boxdev.boxwallet.ui.components.SummaryCard
import com.boxdev.boxwallet.ui.components.TransactionItem
import com.boxdev.boxwallet.ui.components.TransactionSheet

@Preview()
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    @PreviewParameter(HomeUiStatePreviewParameterProvider::class) viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToAnalytics: () -> Unit = {},

    onSeeAllTransactions: () -> Unit = {},
    onSeeAllLoans: () -> Unit = {},
    onNavigateToLoanDetail: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val categoryMap = remember(uiState.categories) { uiState.categories.associateBy { it.id } }
    var showTransactionSheet by remember { mutableStateOf(false) }
    var showLoanSheet by remember { mutableStateOf(false) }
    var selectedTransactionId by remember { mutableStateOf<Long?>(null) }

    val handleHideTransactionSheet = {
        showTransactionSheet = false
        selectedTransactionId = null
    }
    var fabExpanded by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("BoxWallet", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToAnalytics) {
                        Icon(Icons.Default.Analytics, contentDescription = "Analytics")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButtonMenu(
                expanded = fabExpanded,
                button = {
                    ToggleFloatingActionButton(
                        modifier = Modifier.animateFloatingActionButton(
                            visible = true,
                            alignment = Alignment.BottomEnd
                        ),
                        checked = fabExpanded,
                        onCheckedChange = { checked -> fabExpanded = checked },
                        ) {
                            val imageVector by remember {
                                derivedStateOf {
                                    if (checkedProgress > 0.5f) Icons.Default.Close else Icons.Default.Add
                                }
                            }
                            Icon(
                                painter = rememberVectorPainter(imageVector),
                                contentDescription = null,
                                modifier = Modifier.animateIcon({ checkedProgress }),
                            )
                        }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        selectedTransactionId = null
                        showTransactionSheet = true
                    },
                    text = { Text("Add Transaction") },
                    icon = {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = "Receipt icon",
                        )
                    }
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        fabExpanded = false
                        showLoanSheet = true
                    },
                    text = { Text("Add Loan") },
                    icon = {
                        Icon(
                            Icons.Default.CreditCard,
                            contentDescription = "Credit Card icon",
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) { LoadingIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                    ),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    SummaryCard(summary = uiState.financialSummary)
                }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(onClick = onSeeAllTransactions) {
                            Text("See all")
                        }
                    }
                }

                if (uiState.latestTransactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No transactions yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(uiState.latestTransactions, key = { it.id }) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            category = transaction.categoryId?.let { categoryMap[it] },
                            onClick = {
                                selectedTransactionId = transaction.id
                                showTransactionSheet = true
                            }
                        )
                    }
                }

                if (uiState.activeLoans.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Active Loans",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = onSeeAllLoans) {
                                Text("See all")
                            }
                        }
                    }
                    items(uiState.activeLoans, key = { "loan_${it.id}" }) { loan ->
                        LoanCard(
                            loan = loan,
                            onClick = { onNavigateToLoanDetail(loan.id) },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        }
    }

    if (fabExpanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { fabExpanded = false }
        )
    }

    if (showTransactionSheet) {
        TransactionSheet(
            transactionId = selectedTransactionId,
            onDismiss = handleHideTransactionSheet
        )
    }

    if (showLoanSheet) {
        LoanSheet(
            loanId = null,
            onDismiss = { showLoanSheet = false }
        )
    }
}
