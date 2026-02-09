@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
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
    onNavigateToSettings: () -> Unit = {},
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


    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("BoxWallet", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToAnalytics) {
                        Icon(Icons.Default.Analytics, contentDescription = "Analytics")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            var expanded: Boolean by remember { mutableStateOf(false) }

            FloatingActionButtonMenu(
                expanded = expanded,
                modifier = Modifier,
                button = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(if (expanded) TooltipAnchorPosition.Start else TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("Toggle menu") } },
                        state = rememberTooltipState()
                    ) {
                        ToggleFloatingActionButton(
                            modifier = Modifier.animateFloatingActionButton(
                                visible = true,
                                alignment = Alignment.BottomEnd
                            ),
                            checked = expanded,
                            onCheckedChange = { checked -> expanded = checked },
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
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
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
                    onClick = { showLoanSheet = true },
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
                        bottom = 0.dp,
                        top = paddingValues.calculateTopPadding(),
                        end = 16.dp,
                        start = 16.dp
                    ),
            ) {
                item {
                    SummaryCard(summary = uiState.financialSummary)
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                                style = MaterialTheme.typography.bodyMedium
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
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                            onClick = { onNavigateToLoanDetail(loan.id) }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
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
