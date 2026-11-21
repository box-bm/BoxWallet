@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.boxdev.boxwallet.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import com.boxdev.boxwallet.ui.navigation.NavRoutes
import com.boxdev.boxwallet.ui.navigation.TabReselectEffect
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.boxdev.boxwallet.domain.model.Transaction
import com.boxdev.boxwallet.ui.components.AdjustmentDeleteDialog
import com.boxdev.boxwallet.data.local.entity.isAdjustment
import com.boxdev.boxwallet.data.local.entity.isTransfer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.boxdev.boxwallet.R
import com.boxdev.boxwallet.ui.components.AppSearchBar
import com.boxdev.boxwallet.ui.components.BalancePanel
import com.boxdev.boxwallet.ui.components.EmptyState
import com.boxdev.boxwallet.ui.components.LoanRow
import com.boxdev.boxwallet.ui.components.PaneRow
import com.boxdev.boxwallet.ui.components.SectionHeader
import com.boxdev.boxwallet.ui.components.TransactionItem
import com.boxdev.boxwallet.ui.components.transferTitle
import com.boxdev.boxwallet.ui.theme.BoxTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onSeeAllTransactions: () -> Unit = {},
    onSeeAllLoans: () -> Unit = {},
    onNavigateToLoanDetail: (Long) -> Unit = {},
    onEditTransaction: (Long) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onOpenAccount: (Long) -> Unit = {},
    onSeeAccounts: () -> Unit = {},
    onOpenTransfer: (Long) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var pendingAdjustment by remember { mutableStateOf<Transaction?>(null) }
    pendingAdjustment?.let { adjustment ->
        AdjustmentDeleteDialog(
            adjustment = adjustment,
            onDelete = { viewModel.deleteTransaction(adjustment.id); pendingAdjustment = null },
            onDismiss = { pendingAdjustment = null },
        )
    }
    val categoryMap = remember(uiState.categories) { uiState.categories.associateBy { it.id } }
    val twoPane = BoxTheme.layout.twoPane
    val startList = rememberLazyListState()
    val endList = rememberLazyListState()
    TabReselectEffect(NavRoutes.Home.route) {
        // Side by side, so neither scroll waits on the other; the end list is only laid out in two panes
        // (a scroll on a list that is never laid out would never finish, blocking later reselects).
        coroutineScope {
            launch { startList.animateScrollToItem(0) }
            if (twoPane) launch { endList.animateScrollToItem(0) }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            AppSearchBar(
                onClick = onNavigateToSearch,
                trailing = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }

        val balance: LazyListScope.() -> Unit = {
            item(key = "balance") { BalancePanel(summary = uiState.financialSummary, totalBalance = uiState.totalBalance) }
            if (showAccountsRow(uiState.accounts)) {
                item(key = "accounts") {
                    HomeAccountsRow(
                        accounts = uiState.accounts,
                        onOpenAccount = onOpenAccount,
                        onSeeAccounts = onSeeAccounts,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
        val recent: LazyListScope.() -> Unit = {
            item(key = "recent_header") {
                SectionHeader(
                    title = stringResource(R.string.home_recent_transactions),
                    actionLabel = stringResource(R.string.home_see_all).takeIf { uiState.latestTransactions.isNotEmpty() },
                    onAction = onSeeAllTransactions,
                    // In two panes this header opens its column, so it aligns with the balance panel.
                    modifier = Modifier.padding(top = if (twoPane) 0.dp else 16.dp),
                )
            }
            if (uiState.latestTransactions.isEmpty()) {
                item(key = "recent_empty") {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = stringResource(R.string.home_no_transactions),
                        message = stringResource(R.string.home_no_transactions_hint),
                    )
                }
            } else {
                val list = uiState.latestTransactions
                itemsIndexed(list, key = { _, t -> "t_${t.id}" }) { index, transaction ->
                    TransactionItem(
                        transaction = transaction,
                        category = transaction.categoryId?.let { categoryMap[it] },
                        index = index,
                        count = list.size,
                        transferTitle = transaction.transferId?.let { transferTitle(uiState.transferPairs[it], uiState.accountsById) },
                        onClick = {
                            when {
                                transaction.type.isAdjustment -> pendingAdjustment = transaction
                                transaction.type.isTransfer -> onOpenTransfer(transaction.id)
                                else -> onEditTransaction(transaction.id)
                            }
                        },
                        modifier = Modifier.padding(bottom = ListItemDefaults.SegmentedGap),
                    )
                }
            }
        }
        val loans: LazyListScope.() -> Unit = {
            if (uiState.activeLoans.isNotEmpty()) {
                item(key = "loans_header") {
                    SectionHeader(
                        title = stringResource(R.string.home_active_loans),
                        actionLabel = stringResource(R.string.home_see_all),
                        onAction = onSeeAllLoans,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                // One line per loan here; the full cards live on the Loans tab.
                itemsIndexed(uiState.activeLoans, key = { _, l -> "loan_${l.id}" }) { index, loan ->
                    LoanRow(
                        loan = loan,
                        index = index,
                        count = uiState.activeLoans.size,
                        category = loan.categoryId?.let { categoryMap[it] },
                        onClick = { onNavigateToLoanDetail(loan.id) },
                    )
                }
            }
        }

        PaneRow(
            modifier = Modifier.padding(padding),
            first = {
                if (twoPane) {
                    // Unfolded / tablet: money overview and loans on the left, movements on the right.
                    LazyColumn(
                        state = startList,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    ) {
                        balance()
                        loans()
                    }
                } else {
                    LazyColumn(
                        state = startList,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp),
                    ) {
                        balance()
                        recent()
                        loans()
                        item { Spacer(Modifier.height(96.dp)) }
                    }
                }
            },
            second = if (twoPane) {
                {
                    LazyColumn(
                        state = endList,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp),
                    ) {
                        recent()
                    }
                }
            } else null,
        )
    }
}
